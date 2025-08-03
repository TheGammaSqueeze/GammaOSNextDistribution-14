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

#pragma once

#include "common/contextual_callback.h"
#include "hci/address.h"
#include "hci/class_of_device.h"
#include "hci/hci_packets.h"
#include "hci/le_rand_callback.h"

namespace bluetooth {
namespace hci {

class ControllerInterface {
 public:
  ControllerInterface() = default;
  virtual ~ControllerInterface() = default;

  using CompletedAclPacketsCallback =
      common::ContextualCallback<void(uint16_t /* handle */, uint16_t /* num_packets */)>;
  virtual void RegisterCompletedAclPacketsCallback(CompletedAclPacketsCallback cb) = 0;

  virtual void UnregisterCompletedAclPacketsCallback() = 0;

  virtual void RegisterCompletedMonitorAclPacketsCallback(CompletedAclPacketsCallback cb) = 0;
  virtual void UnregisterCompletedMonitorAclPacketsCallback() = 0;

  virtual std::string GetLocalName() const = 0;
  virtual LocalVersionInformation GetLocalVersionInformation() const = 0;

  virtual bool SupportsSimplePairing() const = 0;
  virtual bool SupportsSecureConnections() const = 0;
  virtual bool SupportsSimultaneousLeBrEdr() const = 0;
  virtual bool SupportsInterlacedInquiryScan() const = 0;
  virtual bool SupportsRssiWithInquiryResults() const = 0;
  virtual bool SupportsExtendedInquiryResponse() const = 0;
  virtual bool SupportsRoleSwitch() const = 0;
  virtual bool Supports3SlotPackets() const = 0;
  virtual bool Supports5SlotPackets() const = 0;
  virtual bool SupportsClassic2mPhy() const = 0;
  virtual bool SupportsClassic3mPhy() const = 0;
  virtual bool Supports3SlotEdrPackets() const = 0;
  virtual bool Supports5SlotEdrPackets() const = 0;
  virtual bool SupportsSco() const = 0;
  virtual bool SupportsHv2Packets() const = 0;
  virtual bool SupportsHv3Packets() const = 0;
  virtual bool SupportsEv3Packets() const = 0;
  virtual bool SupportsEv4Packets() const = 0;
  virtual bool SupportsEv5Packets() const = 0;
  virtual bool SupportsEsco2mPhy() const = 0;
  virtual bool SupportsEsco3mPhy() const = 0;
  virtual bool Supports3SlotEscoEdrPackets() const = 0;
  virtual bool SupportsHoldMode() const = 0;
  virtual bool SupportsSniffMode() const = 0;
  virtual bool SupportsParkMode() const = 0;
  virtual bool SupportsNonFlushablePb() const = 0;
  virtual bool SupportsSniffSubrating() const = 0;
  virtual bool SupportsEncryptionPause() const = 0;
  virtual bool SupportsBle() const = 0;

  virtual bool SupportsBleEncryption() const = 0;
  virtual bool SupportsBleConnectionParametersRequest() const = 0;
  virtual bool SupportsBleExtendedReject() const = 0;
  virtual bool SupportsBlePeripheralInitiatedFeaturesExchange() const = 0;
  virtual bool SupportsBlePing() const = 0;
  virtual bool SupportsBleDataPacketLengthExtension() const = 0;
  virtual bool SupportsBlePrivacy() const = 0;
  virtual bool SupportsBleExtendedScannerFilterPolicies() const = 0;
  virtual bool SupportsBle2mPhy() const = 0;
  virtual bool SupportsBleStableModulationIndexTx() const = 0;
  virtual bool SupportsBleStableModulationIndexRx() const = 0;
  virtual bool SupportsBleCodedPhy() const = 0;
  virtual bool SupportsBleExtendedAdvertising() const = 0;
  virtual bool SupportsBlePeriodicAdvertising() const = 0;
  virtual bool SupportsBleChannelSelectionAlgorithm2() const = 0;
  virtual bool SupportsBlePowerClass1() const = 0;
  virtual bool SupportsBleMinimumUsedChannels() const = 0;
  virtual bool SupportsBleConnectionCteRequest() const = 0;
  virtual bool SupportsBleConnectionCteResponse() const = 0;
  virtual bool SupportsBleConnectionlessCteTransmitter() const = 0;
  virtual bool SupportsBleConnectionlessCteReceiver() const = 0;
  virtual bool SupportsBleAntennaSwitchingDuringCteTx() const = 0;
  virtual bool SupportsBleAntennaSwitchingDuringCteRx() const = 0;
  virtual bool SupportsBleReceivingConstantToneExtensions() const = 0;
  virtual bool SupportsBlePeriodicAdvertisingSyncTransferSender() const = 0;
  virtual bool SupportsBlePeriodicAdvertisingSyncTransferRecipient() const = 0;
  virtual bool SupportsBleSleepClockAccuracyUpdates() const = 0;
  virtual bool SupportsBleRemotePublicKeyValidation() const = 0;
  virtual bool SupportsBleConnectedIsochronousStreamCentral() const = 0;
  virtual bool SupportsBleConnectedIsochronousStreamPeripheral() const = 0;
  virtual bool SupportsBleIsochronousBroadcaster() const = 0;
  virtual bool SupportsBleSynchronizedReceiver() const = 0;
  virtual bool SupportsBleIsochronousChannelsHostSupport() const = 0;
  virtual bool SupportsBlePowerControlRequest() const = 0;
  virtual bool SupportsBlePowerChangeIndication() const = 0;
  virtual bool SupportsBlePathLossMonitoring() const = 0;
  virtual bool SupportsBlePeriodicAdvertisingAdi() const = 0;
  virtual bool SupportsBleConnectionSubrating() const = 0;
  virtual bool SupportsBleConnectionSubratingHost() const = 0;

  virtual uint16_t GetAclPacketLength() const = 0;

  virtual uint16_t GetNumAclPacketBuffers() const = 0;

  virtual uint8_t GetScoPacketLength() const = 0;

  virtual uint16_t GetNumScoPacketBuffers() const = 0;

  virtual Address GetMacAddress() const = 0;

  virtual void SetEventMask(uint64_t event_mask) = 0;

  virtual void Reset() = 0;

  virtual void LeRand(LeRandCallback cb) = 0;

  virtual void SetEventFilterClearAll() = 0;

  virtual void SetEventFilterInquiryResultAllDevices() = 0;

  virtual void SetEventFilterInquiryResultClassOfDevice(
      ClassOfDevice class_of_device, ClassOfDevice class_of_device_mask) = 0;

  virtual void SetEventFilterInquiryResultAddress(Address address) = 0;

  virtual void SetEventFilterConnectionSetupAllDevices(AutoAcceptFlag auto_accept_flag) = 0;

  virtual void SetEventFilterConnectionSetupClassOfDevice(
      ClassOfDevice class_of_device,
      ClassOfDevice class_of_device_mask,
      AutoAcceptFlag auto_accept_flag) = 0;

  virtual void SetEventFilterConnectionSetupAddress(
      Address address, AutoAcceptFlag auto_accept_flag) = 0;

  virtual void WriteLocalName(std::string local_name) = 0;

  virtual void HostBufferSize(
      uint16_t host_acl_data_packet_length,
      uint8_t host_synchronous_data_packet_length,
      uint16_t host_total_num_acl_data_packets,
      uint16_t host_total_num_synchronous_data_packets) = 0;

  // LE controller commands
  virtual void LeSetEventMask(uint64_t le_event_mask) = 0;

  virtual LeBufferSize GetLeBufferSize() const = 0;

  virtual uint64_t GetLeSupportedStates() const = 0;

  virtual LeBufferSize GetControllerIsoBufferSize() const = 0;

  virtual uint64_t GetControllerLeLocalSupportedFeatures() const = 0;

  virtual uint8_t GetLeFilterAcceptListSize() const = 0;

  virtual uint8_t GetLeResolvingListSize() const = 0;

  virtual LeMaximumDataLength GetLeMaximumDataLength() const = 0;

  virtual uint16_t GetLeMaximumAdvertisingDataLength() const = 0;

  virtual uint16_t GetLeSuggestedDefaultDataLength() const = 0;

  virtual uint8_t GetLeNumberOfSupportedAdverisingSets() const = 0;

  virtual uint8_t GetLePeriodicAdvertiserListSize() const = 0;

  // 7.4.8 Read Local Supported Codecs command v1 only returns codecs on the BR/EDR transport
  virtual std::vector<uint8_t> GetLocalSupportedBrEdrCodecIds() const = 0;

  struct VendorCapabilities {
    uint8_t is_supported_;
    uint8_t max_advt_instances_;
    uint8_t offloaded_resolution_of_private_address_;
    uint16_t total_scan_results_storage_;
    uint8_t max_irk_list_sz_;
    uint8_t filtering_support_;
    uint8_t max_filter_;
    uint8_t activity_energy_info_support_;
    uint16_t version_supported_;
    uint16_t total_num_of_advt_tracked_;
    uint8_t extended_scan_support_;
    uint8_t debug_logging_supported_;
    uint8_t le_address_generation_offloading_support_;
    uint32_t a2dp_source_offload_capability_mask_;
    uint8_t bluetooth_quality_report_support_;
    uint32_t dynamic_audio_buffer_support_;
    uint8_t a2dp_offload_v2_support_;
  };

  virtual uint32_t GetDabSupportedCodecs() const = 0;
  virtual const std::array<DynamicAudioBufferCodecCapability, 32>& GetDabCodecCapabilities()
      const = 0;

  virtual void SetDabAudioBufferTime(uint16_t buffer_time_ms) = 0;

  virtual VendorCapabilities GetVendorCapabilities() const = 0;

  virtual bool IsSupported(OpCode op_code) const = 0;
};

}  // namespace hci
}  // namespace bluetooth
