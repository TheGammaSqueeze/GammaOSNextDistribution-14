//! reimport of generated packets (to go away once rust_genrule exists)

#![allow(clippy::all)]
#![allow(unused)]
#![allow(missing_docs)]

pub mod hci {
    include!(concat!(env!("OUT_DIR"), "/hci_packets.rs"));

    pub const EMPTY_ADDRESS: Address = Address(0x000000000000);
    pub const ANY_ADDRESS: Address = Address(0xffffffffffff);

    impl fmt::Display for Address {
        fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
            let bytes = u64::to_le_bytes(self.0);
            write!(
                f,
                "{:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
                bytes[5], bytes[4], bytes[3], bytes[2], bytes[1], bytes[0],
            )
        }
    }

    impl From<&[u8; 6]> for Address {
        fn from(bytes: &[u8; 6]) -> Self {
            Self(u64::from_le_bytes([
                bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], 0, 0,
            ]))
        }
    }

    impl From<Address> for [u8; 6] {
        fn from(Address(addr): Address) -> Self {
            let bytes = u64::to_le_bytes(addr);
            bytes[0..6].try_into().unwrap()
        }
    }

    impl Address {
        pub fn is_empty(&self) -> bool {
            *self == EMPTY_ADDRESS
        }
    }

    impl fmt::Display for ClassOfDevice {
        fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
            write!(
                f,
                "{:03X}-{:01X}-{:02X}",
                (self.0 >> 12) & 0xfff,
                (self.0 >> 8) & 0xf,
                self.0 & 0xff,
            )
        }
    }

    pub trait CommandExpectations {
        type ResponseType;
        fn _to_response_type(pkt: Event) -> Self::ResponseType;
    }

    macro_rules! impl_command_expectations (
        ($cmd:ident, $evt:ident) => {
            impl CommandExpectations for $cmd {
                type ResponseType = $evt;
                fn _to_response_type(pkt: Event) -> Self::ResponseType {
                    $evt::new(pkt.event.clone()).unwrap()
                }
            }
        }
    );

    impl_command_expectations!(ResetBuilder, ResetComplete);
    impl_command_expectations!(SetEventMaskBuilder, SetEventMaskComplete);
    impl_command_expectations!(LeSetEventMaskBuilder, LeSetEventMaskComplete);
    impl_command_expectations!(WriteSimplePairingModeBuilder, WriteSimplePairingModeComplete);
    impl_command_expectations!(WriteLeHostSupportBuilder, WriteLeHostSupportComplete);
    impl_command_expectations!(WriteLocalNameBuilder, WriteLocalNameComplete);
    impl_command_expectations!(WriteLocalName, WriteLocalNameComplete);
    impl_command_expectations!(ReadLocalNameBuilder, ReadLocalNameComplete);
    impl_command_expectations!(
        ReadLocalVersionInformationBuilder,
        ReadLocalVersionInformationComplete
    );
    impl_command_expectations!(
        ReadLocalSupportedCommandsBuilder,
        ReadLocalSupportedCommandsComplete
    );
    impl_command_expectations!(ReadBufferSizeBuilder, ReadBufferSizeComplete);
    impl_command_expectations!(LeReadBufferSizeV2Builder, LeReadBufferSizeV2Complete);
    impl_command_expectations!(LeReadBufferSizeV1Builder, LeReadBufferSizeV1Complete);
    impl_command_expectations!(
        ReadLocalSupportedFeaturesBuilder,
        ReadLocalSupportedFeaturesComplete
    );
    impl_command_expectations!(
        LeReadLocalSupportedFeaturesBuilder,
        LeReadLocalSupportedFeaturesComplete
    );
    impl_command_expectations!(LeReadSupportedStatesBuilder, LeReadSupportedStatesComplete);
    impl_command_expectations!(
        LeReadFilterAcceptListSizeBuilder,
        LeReadFilterAcceptListSizeComplete
    );
    impl_command_expectations!(LeReadResolvingListSizeBuilder, LeReadResolvingListSizeComplete);
    impl_command_expectations!(LeReadMaximumDataLengthBuilder, LeReadMaximumDataLengthComplete);
    impl_command_expectations!(
        LeReadSuggestedDefaultDataLengthBuilder,
        LeReadSuggestedDefaultDataLengthComplete
    );
    impl_command_expectations!(
        LeReadMaximumAdvertisingDataLengthBuilder,
        LeReadMaximumAdvertisingDataLengthComplete
    );
    impl_command_expectations!(
        LeReadNumberOfSupportedAdvertisingSetsBuilder,
        LeReadNumberOfSupportedAdvertisingSetsComplete
    );
    impl_command_expectations!(
        LeReadPeriodicAdvertiserListSizeBuilder,
        LeReadPeriodicAdvertiserListSizeComplete
    );
    impl_command_expectations!(LeSetHostFeatureBuilder, LeSetHostFeatureComplete);
    impl_command_expectations!(ReadBdAddrBuilder, ReadBdAddrComplete);
    impl_command_expectations!(ReadLocalExtendedFeaturesBuilder, ReadLocalExtendedFeaturesComplete);
    impl_command_expectations!(CreateConnectionBuilder, CreateConnectionStatus);
    impl_command_expectations!(CreateConnectionCancelBuilder, CreateConnectionCancelComplete);
    impl_command_expectations!(DisconnectBuilder, DisconnectStatus);
    impl_command_expectations!(RejectConnectionRequestBuilder, RejectConnectionRequestStatus);
    impl_command_expectations!(AcceptConnectionRequestBuilder, AcceptConnectionRequestStatus);

    impl TryFrom<OpCode> for OpCodeIndex {
        type Error = &'static str;
        fn try_from(value: OpCode) -> std::result::Result<Self, Self::Error> {
            match value {
                OpCode::Inquiry => Ok(OpCodeIndex::Inquiry),
                OpCode::InquiryCancel => Ok(OpCodeIndex::InquiryCancel),
                OpCode::PeriodicInquiryMode => Ok(OpCodeIndex::PeriodicInquiryMode),
                OpCode::ExitPeriodicInquiryMode => Ok(OpCodeIndex::ExitPeriodicInquiryMode),
                OpCode::CreateConnection => Ok(OpCodeIndex::CreateConnection),
                OpCode::Disconnect => Ok(OpCodeIndex::Disconnect),
                OpCode::AddScoConnection => Ok(OpCodeIndex::AddScoConnection),
                OpCode::CreateConnectionCancel => Ok(OpCodeIndex::CreateConnectionCancel),
                OpCode::AcceptConnectionRequest => Ok(OpCodeIndex::AcceptConnectionRequest),
                OpCode::RejectConnectionRequest => Ok(OpCodeIndex::RejectConnectionRequest),
                OpCode::LinkKeyRequestReply => Ok(OpCodeIndex::LinkKeyRequestReply),
                OpCode::LinkKeyRequestNegativeReply => Ok(OpCodeIndex::LinkKeyRequestNegativeReply),
                OpCode::PinCodeRequestReply => Ok(OpCodeIndex::PinCodeRequestReply),
                OpCode::PinCodeRequestNegativeReply => Ok(OpCodeIndex::PinCodeRequestNegativeReply),
                OpCode::ChangeConnectionPacketType => Ok(OpCodeIndex::ChangeConnectionPacketType),
                OpCode::AuthenticationRequested => Ok(OpCodeIndex::AuthenticationRequested),
                OpCode::SetConnectionEncryption => Ok(OpCodeIndex::SetConnectionEncryption),
                OpCode::ChangeConnectionLinkKey => Ok(OpCodeIndex::ChangeConnectionLinkKey),
                OpCode::CentralLinkKey => Ok(OpCodeIndex::CentralLinkKey),
                OpCode::RemoteNameRequest => Ok(OpCodeIndex::RemoteNameRequest),
                OpCode::RemoteNameRequestCancel => Ok(OpCodeIndex::RemoteNameRequestCancel),
                OpCode::ReadRemoteSupportedFeatures => Ok(OpCodeIndex::ReadRemoteSupportedFeatures),
                OpCode::ReadRemoteExtendedFeatures => Ok(OpCodeIndex::ReadRemoteExtendedFeatures),
                OpCode::ReadRemoteVersionInformation => {
                    Ok(OpCodeIndex::ReadRemoteVersionInformation)
                }
                OpCode::ReadClockOffset => Ok(OpCodeIndex::ReadClockOffset),
                OpCode::ReadLmpHandle => Ok(OpCodeIndex::ReadLmpHandle),
                OpCode::HoldMode => Ok(OpCodeIndex::HoldMode),
                OpCode::SniffMode => Ok(OpCodeIndex::SniffMode),
                OpCode::ExitSniffMode => Ok(OpCodeIndex::ExitSniffMode),
                OpCode::QosSetup => Ok(OpCodeIndex::QosSetup),
                OpCode::RoleDiscovery => Ok(OpCodeIndex::RoleDiscovery),
                OpCode::SwitchRole => Ok(OpCodeIndex::SwitchRole),
                OpCode::ReadLinkPolicySettings => Ok(OpCodeIndex::ReadLinkPolicySettings),
                OpCode::WriteLinkPolicySettings => Ok(OpCodeIndex::WriteLinkPolicySettings),
                OpCode::ReadDefaultLinkPolicySettings => {
                    Ok(OpCodeIndex::ReadDefaultLinkPolicySettings)
                }
                OpCode::WriteDefaultLinkPolicySettings => {
                    Ok(OpCodeIndex::WriteDefaultLinkPolicySettings)
                }
                OpCode::FlowSpecification => Ok(OpCodeIndex::FlowSpecification),
                OpCode::SetEventMask => Ok(OpCodeIndex::SetEventMask),
                OpCode::Reset => Ok(OpCodeIndex::Reset),
                OpCode::SetEventFilter => Ok(OpCodeIndex::SetEventFilter),
                OpCode::Flush => Ok(OpCodeIndex::Flush),
                OpCode::ReadPinType => Ok(OpCodeIndex::ReadPinType),
                OpCode::WritePinType => Ok(OpCodeIndex::WritePinType),
                OpCode::ReadStoredLinkKey => Ok(OpCodeIndex::ReadStoredLinkKey),
                OpCode::WriteStoredLinkKey => Ok(OpCodeIndex::WriteStoredLinkKey),
                OpCode::DeleteStoredLinkKey => Ok(OpCodeIndex::DeleteStoredLinkKey),
                OpCode::WriteLocalName => Ok(OpCodeIndex::WriteLocalName),
                OpCode::ReadLocalName => Ok(OpCodeIndex::ReadLocalName),
                OpCode::ReadConnectionAcceptTimeout => Ok(OpCodeIndex::ReadConnectionAcceptTimeout),
                OpCode::WriteConnectionAcceptTimeout => {
                    Ok(OpCodeIndex::WriteConnectionAcceptTimeout)
                }
                OpCode::ReadPageTimeout => Ok(OpCodeIndex::ReadPageTimeout),
                OpCode::WritePageTimeout => Ok(OpCodeIndex::WritePageTimeout),
                OpCode::ReadScanEnable => Ok(OpCodeIndex::ReadScanEnable),
                OpCode::WriteScanEnable => Ok(OpCodeIndex::WriteScanEnable),
                OpCode::ReadPageScanActivity => Ok(OpCodeIndex::ReadPageScanActivity),
                OpCode::WritePageScanActivity => Ok(OpCodeIndex::WritePageScanActivity),
                OpCode::ReadInquiryScanActivity => Ok(OpCodeIndex::ReadInquiryScanActivity),
                OpCode::WriteInquiryScanActivity => Ok(OpCodeIndex::WriteInquiryScanActivity),
                OpCode::ReadAuthenticationEnable => Ok(OpCodeIndex::ReadAuthenticationEnable),
                OpCode::WriteAuthenticationEnable => Ok(OpCodeIndex::WriteAuthenticationEnable),
                OpCode::ReadClassOfDevice => Ok(OpCodeIndex::ReadClassOfDevice),
                OpCode::WriteClassOfDevice => Ok(OpCodeIndex::WriteClassOfDevice),
                OpCode::ReadVoiceSetting => Ok(OpCodeIndex::ReadVoiceSetting),
                OpCode::WriteVoiceSetting => Ok(OpCodeIndex::WriteVoiceSetting),
                OpCode::ReadAutomaticFlushTimeout => Ok(OpCodeIndex::ReadAutomaticFlushTimeout),
                OpCode::WriteAutomaticFlushTimeout => Ok(OpCodeIndex::WriteAutomaticFlushTimeout),
                OpCode::ReadNumBroadcastRetransmits => Ok(OpCodeIndex::ReadNumBroadcastRetransmits),
                OpCode::WriteNumBroadcastRetransmits => {
                    Ok(OpCodeIndex::WriteNumBroadcastRetransmits)
                }
                OpCode::ReadHoldModeActivity => Ok(OpCodeIndex::ReadHoldModeActivity),
                OpCode::WriteHoldModeActivity => Ok(OpCodeIndex::WriteHoldModeActivity),
                OpCode::ReadTransmitPowerLevel => Ok(OpCodeIndex::ReadTransmitPowerLevel),
                OpCode::ReadSynchronousFlowControlEnable => {
                    Ok(OpCodeIndex::ReadSynchronousFlowControlEnable)
                }
                OpCode::WriteSynchronousFlowControlEnable => {
                    Ok(OpCodeIndex::WriteSynchronousFlowControlEnable)
                }
                OpCode::SetControllerToHostFlowControl => {
                    Ok(OpCodeIndex::SetControllerToHostFlowControl)
                }
                OpCode::HostBufferSize => Ok(OpCodeIndex::HostBufferSize),
                OpCode::HostNumberOfCompletedPackets => {
                    Ok(OpCodeIndex::HostNumberOfCompletedPackets)
                }
                OpCode::ReadLinkSupervisionTimeout => Ok(OpCodeIndex::ReadLinkSupervisionTimeout),
                OpCode::WriteLinkSupervisionTimeout => Ok(OpCodeIndex::WriteLinkSupervisionTimeout),
                OpCode::ReadNumberOfSupportedIac => Ok(OpCodeIndex::ReadNumberOfSupportedIac),
                OpCode::ReadCurrentIacLap => Ok(OpCodeIndex::ReadCurrentIacLap),
                OpCode::WriteCurrentIacLap => Ok(OpCodeIndex::WriteCurrentIacLap),
                OpCode::SetAfhHostChannelClassification => {
                    Ok(OpCodeIndex::SetAfhHostChannelClassification)
                }
                OpCode::ReadInquiryScanType => Ok(OpCodeIndex::ReadInquiryScanType),
                OpCode::WriteInquiryScanType => Ok(OpCodeIndex::WriteInquiryScanType),
                OpCode::ReadInquiryMode => Ok(OpCodeIndex::ReadInquiryMode),
                OpCode::WriteInquiryMode => Ok(OpCodeIndex::WriteInquiryMode),
                OpCode::ReadPageScanType => Ok(OpCodeIndex::ReadPageScanType),
                OpCode::WritePageScanType => Ok(OpCodeIndex::WritePageScanType),
                OpCode::ReadAfhChannelAssessmentMode => {
                    Ok(OpCodeIndex::ReadAfhChannelAssessmentMode)
                }
                OpCode::WriteAfhChannelAssessmentMode => {
                    Ok(OpCodeIndex::WriteAfhChannelAssessmentMode)
                }
                OpCode::ReadLocalVersionInformation => Ok(OpCodeIndex::ReadLocalVersionInformation),
                OpCode::ReadLocalSupportedFeatures => Ok(OpCodeIndex::ReadLocalSupportedFeatures),
                OpCode::ReadLocalExtendedFeatures => Ok(OpCodeIndex::ReadLocalExtendedFeatures),
                OpCode::ReadBufferSize => Ok(OpCodeIndex::ReadBufferSize),
                OpCode::ReadBdAddr => Ok(OpCodeIndex::ReadBdAddr),
                OpCode::ReadFailedContactCounter => Ok(OpCodeIndex::ReadFailedContactCounter),
                OpCode::ResetFailedContactCounter => Ok(OpCodeIndex::ResetFailedContactCounter),
                OpCode::ReadLinkQuality => Ok(OpCodeIndex::ReadLinkQuality),
                OpCode::ReadRssi => Ok(OpCodeIndex::ReadRssi),
                OpCode::ReadAfhChannelMap => Ok(OpCodeIndex::ReadAfhChannelMap),
                OpCode::ReadClock => Ok(OpCodeIndex::ReadClock),
                OpCode::ReadLoopbackMode => Ok(OpCodeIndex::ReadLoopbackMode),
                OpCode::WriteLoopbackMode => Ok(OpCodeIndex::WriteLoopbackMode),
                OpCode::EnableDeviceUnderTestMode => Ok(OpCodeIndex::EnableDeviceUnderTestMode),
                OpCode::SetupSynchronousConnection => Ok(OpCodeIndex::SetupSynchronousConnection),
                OpCode::AcceptSynchronousConnection => Ok(OpCodeIndex::AcceptSynchronousConnection),
                OpCode::RejectSynchronousConnection => Ok(OpCodeIndex::RejectSynchronousConnection),
                OpCode::ReadExtendedInquiryResponse => Ok(OpCodeIndex::ReadExtendedInquiryResponse),
                OpCode::WriteExtendedInquiryResponse => {
                    Ok(OpCodeIndex::WriteExtendedInquiryResponse)
                }
                OpCode::RefreshEncryptionKey => Ok(OpCodeIndex::RefreshEncryptionKey),
                OpCode::SniffSubrating => Ok(OpCodeIndex::SniffSubrating),
                OpCode::ReadSimplePairingMode => Ok(OpCodeIndex::ReadSimplePairingMode),
                OpCode::WriteSimplePairingMode => Ok(OpCodeIndex::WriteSimplePairingMode),
                OpCode::ReadLocalOobData => Ok(OpCodeIndex::ReadLocalOobData),
                OpCode::ReadInquiryResponseTransmitPowerLevel => {
                    Ok(OpCodeIndex::ReadInquiryResponseTransmitPowerLevel)
                }
                OpCode::WriteInquiryTransmitPowerLevel => {
                    Ok(OpCodeIndex::WriteInquiryTransmitPowerLevel)
                }
                OpCode::ReadDefaultErroneousDataReporting => {
                    Ok(OpCodeIndex::ReadDefaultErroneousDataReporting)
                }
                OpCode::WriteDefaultErroneousDataReporting => {
                    Ok(OpCodeIndex::WriteDefaultErroneousDataReporting)
                }
                OpCode::IoCapabilityRequestReply => Ok(OpCodeIndex::IoCapabilityRequestReply),
                OpCode::UserConfirmationRequestReply => {
                    Ok(OpCodeIndex::UserConfirmationRequestReply)
                }
                OpCode::UserConfirmationRequestNegativeReply => {
                    Ok(OpCodeIndex::UserConfirmationRequestNegativeReply)
                }
                OpCode::UserPasskeyRequestReply => Ok(OpCodeIndex::UserPasskeyRequestReply),
                OpCode::UserPasskeyRequestNegativeReply => {
                    Ok(OpCodeIndex::UserPasskeyRequestNegativeReply)
                }
                OpCode::RemoteOobDataRequestReply => Ok(OpCodeIndex::RemoteOobDataRequestReply),
                OpCode::WriteSimplePairingDebugMode => Ok(OpCodeIndex::WriteSimplePairingDebugMode),
                OpCode::EnhancedFlush => Ok(OpCodeIndex::EnhancedFlush),
                OpCode::RemoteOobDataRequestNegativeReply => {
                    Ok(OpCodeIndex::RemoteOobDataRequestNegativeReply)
                }
                OpCode::SendKeypressNotification => Ok(OpCodeIndex::SendKeypressNotification),
                OpCode::IoCapabilityRequestNegativeReply => {
                    Ok(OpCodeIndex::IoCapabilityRequestNegativeReply)
                }
                OpCode::ReadEncryptionKeySize => Ok(OpCodeIndex::ReadEncryptionKeySize),
                OpCode::SetEventMaskPage2 => Ok(OpCodeIndex::SetEventMaskPage2),
                OpCode::ReadFlowControlMode => Ok(OpCodeIndex::ReadFlowControlMode),
                OpCode::WriteFlowControlMode => Ok(OpCodeIndex::WriteFlowControlMode),
                OpCode::ReadDataBlockSize => Ok(OpCodeIndex::ReadDataBlockSize),
                OpCode::ReadEnhancedTransmitPowerLevel => {
                    Ok(OpCodeIndex::ReadEnhancedTransmitPowerLevel)
                }
                OpCode::ReadLeHostSupport => Ok(OpCodeIndex::ReadLeHostSupport),
                OpCode::WriteLeHostSupport => Ok(OpCodeIndex::WriteLeHostSupport),
                OpCode::LeSetEventMask => Ok(OpCodeIndex::LeSetEventMask),
                OpCode::LeReadBufferSizeV1 => Ok(OpCodeIndex::LeReadBufferSizeV1),
                OpCode::LeReadLocalSupportedFeatures => {
                    Ok(OpCodeIndex::LeReadLocalSupportedFeatures)
                }
                OpCode::LeSetRandomAddress => Ok(OpCodeIndex::LeSetRandomAddress),
                OpCode::LeSetAdvertisingParameters => Ok(OpCodeIndex::LeSetAdvertisingParameters),
                OpCode::LeReadAdvertisingPhysicalChannelTxPower => {
                    Ok(OpCodeIndex::LeReadAdvertisingPhysicalChannelTxPower)
                }
                OpCode::LeSetAdvertisingData => Ok(OpCodeIndex::LeSetAdvertisingData),
                OpCode::LeSetScanResponseData => Ok(OpCodeIndex::LeSetScanResponseData),
                OpCode::LeSetAdvertisingEnable => Ok(OpCodeIndex::LeSetAdvertisingEnable),
                OpCode::LeSetScanParameters => Ok(OpCodeIndex::LeSetScanParameters),
                OpCode::LeSetScanEnable => Ok(OpCodeIndex::LeSetScanEnable),
                OpCode::LeCreateConnection => Ok(OpCodeIndex::LeCreateConnection),
                OpCode::LeCreateConnectionCancel => Ok(OpCodeIndex::LeCreateConnectionCancel),
                OpCode::LeReadFilterAcceptListSize => Ok(OpCodeIndex::LeReadFilterAcceptListSize),
                OpCode::LeClearFilterAcceptList => Ok(OpCodeIndex::LeClearFilterAcceptList),
                OpCode::LeAddDeviceToFilterAcceptList => {
                    Ok(OpCodeIndex::LeAddDeviceToFilterAcceptList)
                }
                OpCode::LeRemoveDeviceFromFilterAcceptList => {
                    Ok(OpCodeIndex::LeRemoveDeviceFromFilterAcceptList)
                }
                OpCode::LeConnectionUpdate => Ok(OpCodeIndex::LeConnectionUpdate),
                OpCode::LeSetHostChannelClassification => {
                    Ok(OpCodeIndex::LeSetHostChannelClassification)
                }
                OpCode::LeReadChannelMap => Ok(OpCodeIndex::LeReadChannelMap),
                OpCode::LeReadRemoteFeatures => Ok(OpCodeIndex::LeReadRemoteFeatures),
                OpCode::LeEncrypt => Ok(OpCodeIndex::LeEncrypt),
                OpCode::LeRand => Ok(OpCodeIndex::LeRand),
                OpCode::LeStartEncryption => Ok(OpCodeIndex::LeStartEncryption),
                OpCode::LeLongTermKeyRequestReply => Ok(OpCodeIndex::LeLongTermKeyRequestReply),
                OpCode::LeLongTermKeyRequestNegativeReply => {
                    Ok(OpCodeIndex::LeLongTermKeyRequestNegativeReply)
                }
                OpCode::LeReadSupportedStates => Ok(OpCodeIndex::LeReadSupportedStates),
                OpCode::LeReceiverTestV1 => Ok(OpCodeIndex::LeReceiverTestV1),
                OpCode::LeTransmitterTestV1 => Ok(OpCodeIndex::LeTransmitterTestV1),
                OpCode::LeTestEnd => Ok(OpCodeIndex::LeTestEnd),
                OpCode::EnhancedSetupSynchronousConnection => {
                    Ok(OpCodeIndex::EnhancedSetupSynchronousConnection)
                }
                OpCode::EnhancedAcceptSynchronousConnection => {
                    Ok(OpCodeIndex::EnhancedAcceptSynchronousConnection)
                }
                OpCode::ReadLocalSupportedCodecsV1 => Ok(OpCodeIndex::ReadLocalSupportedCodecsV1),
                OpCode::SetMwsChannelParameters => Ok(OpCodeIndex::SetMwsChannelParameters),
                OpCode::SetExternalFrameConfiguration => {
                    Ok(OpCodeIndex::SetExternalFrameConfiguration)
                }
                OpCode::SetMwsSignaling => Ok(OpCodeIndex::SetMwsSignaling),
                OpCode::SetMwsTransportLayer => Ok(OpCodeIndex::SetMwsTransportLayer),
                OpCode::SetMwsScanFrequencyTable => Ok(OpCodeIndex::SetMwsScanFrequencyTable),
                OpCode::GetMwsTransportLayerConfiguration => {
                    Ok(OpCodeIndex::GetMwsTransportLayerConfiguration)
                }
                OpCode::SetMwsPatternConfiguration => Ok(OpCodeIndex::SetMwsPatternConfiguration),
                OpCode::SetTriggeredClockCapture => Ok(OpCodeIndex::SetTriggeredClockCapture),
                OpCode::TruncatedPage => Ok(OpCodeIndex::TruncatedPage),
                OpCode::TruncatedPageCancel => Ok(OpCodeIndex::TruncatedPageCancel),
                OpCode::SetConnectionlessPeripheralBroadcast => {
                    Ok(OpCodeIndex::SetConnectionlessPeripheralBroadcast)
                }
                OpCode::SetConnectionlessPeripheralBroadcastReceive => {
                    Ok(OpCodeIndex::SetConnectionlessPeripheralBroadcastReceive)
                }
                OpCode::StartSynchronizationTrain => Ok(OpCodeIndex::StartSynchronizationTrain),
                OpCode::ReceiveSynchronizationTrain => Ok(OpCodeIndex::ReceiveSynchronizationTrain),
                OpCode::SetReservedLtAddr => Ok(OpCodeIndex::SetReservedLtAddr),
                OpCode::DeleteReservedLtAddr => Ok(OpCodeIndex::DeleteReservedLtAddr),
                OpCode::SetConnectionlessPeripheralBroadcastData => {
                    Ok(OpCodeIndex::SetConnectionlessPeripheralBroadcastData)
                }
                OpCode::ReadSynchronizationTrainParameters => {
                    Ok(OpCodeIndex::ReadSynchronizationTrainParameters)
                }
                OpCode::WriteSynchronizationTrainParameters => {
                    Ok(OpCodeIndex::WriteSynchronizationTrainParameters)
                }
                OpCode::RemoteOobExtendedDataRequestReply => {
                    Ok(OpCodeIndex::RemoteOobExtendedDataRequestReply)
                }
                OpCode::ReadSecureConnectionsHostSupport => {
                    Ok(OpCodeIndex::ReadSecureConnectionsHostSupport)
                }
                OpCode::WriteSecureConnectionsHostSupport => {
                    Ok(OpCodeIndex::WriteSecureConnectionsHostSupport)
                }
                OpCode::ReadAuthenticatedPayloadTimeout => {
                    Ok(OpCodeIndex::ReadAuthenticatedPayloadTimeout)
                }
                OpCode::WriteAuthenticatedPayloadTimeout => {
                    Ok(OpCodeIndex::WriteAuthenticatedPayloadTimeout)
                }
                OpCode::ReadLocalOobExtendedData => Ok(OpCodeIndex::ReadLocalOobExtendedData),
                OpCode::WriteSecureConnectionsTestMode => {
                    Ok(OpCodeIndex::WriteSecureConnectionsTestMode)
                }
                OpCode::ReadExtendedPageTimeout => Ok(OpCodeIndex::ReadExtendedPageTimeout),
                OpCode::WriteExtendedPageTimeout => Ok(OpCodeIndex::WriteExtendedPageTimeout),
                OpCode::ReadExtendedInquiryLength => Ok(OpCodeIndex::ReadExtendedInquiryLength),
                OpCode::WriteExtendedInquiryLength => Ok(OpCodeIndex::WriteExtendedInquiryLength),
                OpCode::LeRemoteConnectionParameterRequestReply => {
                    Ok(OpCodeIndex::LeRemoteConnectionParameterRequestReply)
                }
                OpCode::LeRemoteConnectionParameterRequestNegativeReply => {
                    Ok(OpCodeIndex::LeRemoteConnectionParameterRequestNegativeReply)
                }
                OpCode::LeSetDataLength => Ok(OpCodeIndex::LeSetDataLength),
                OpCode::LeReadSuggestedDefaultDataLength => {
                    Ok(OpCodeIndex::LeReadSuggestedDefaultDataLength)
                }
                OpCode::LeWriteSuggestedDefaultDataLength => {
                    Ok(OpCodeIndex::LeWriteSuggestedDefaultDataLength)
                }
                OpCode::LeReadLocalP256PublicKey => Ok(OpCodeIndex::LeReadLocalP256PublicKey),
                OpCode::LeGenerateDhkeyV1 => Ok(OpCodeIndex::LeGenerateDhkeyV1),
                OpCode::LeAddDeviceToResolvingList => Ok(OpCodeIndex::LeAddDeviceToResolvingList),
                OpCode::LeRemoveDeviceFromResolvingList => {
                    Ok(OpCodeIndex::LeRemoveDeviceFromResolvingList)
                }
                OpCode::LeClearResolvingList => Ok(OpCodeIndex::LeClearResolvingList),
                OpCode::LeReadResolvingListSize => Ok(OpCodeIndex::LeReadResolvingListSize),
                OpCode::LeReadPeerResolvableAddress => Ok(OpCodeIndex::LeReadPeerResolvableAddress),
                OpCode::LeReadLocalResolvableAddress => {
                    Ok(OpCodeIndex::LeReadLocalResolvableAddress)
                }
                OpCode::LeSetAddressResolutionEnable => {
                    Ok(OpCodeIndex::LeSetAddressResolutionEnable)
                }
                OpCode::LeSetResolvablePrivateAddressTimeout => {
                    Ok(OpCodeIndex::LeSetResolvablePrivateAddressTimeout)
                }
                OpCode::LeReadMaximumDataLength => Ok(OpCodeIndex::LeReadMaximumDataLength),
                OpCode::LeReadPhy => Ok(OpCodeIndex::LeReadPhy),
                OpCode::LeSetDefaultPhy => Ok(OpCodeIndex::LeSetDefaultPhy),
                OpCode::LeSetPhy => Ok(OpCodeIndex::LeSetPhy),
                OpCode::LeReceiverTestV2 => Ok(OpCodeIndex::LeReceiverTestV2),
                OpCode::LeTransmitterTestV2 => Ok(OpCodeIndex::LeTransmitterTestV2),
                OpCode::LeSetAdvertisingSetRandomAddress => {
                    Ok(OpCodeIndex::LeSetAdvertisingSetRandomAddress)
                }
                OpCode::LeSetExtendedAdvertisingParameters => {
                    Ok(OpCodeIndex::LeSetExtendedAdvertisingParameters)
                }
                OpCode::LeSetExtendedAdvertisingData => {
                    Ok(OpCodeIndex::LeSetExtendedAdvertisingData)
                }
                OpCode::LeSetExtendedScanResponseData => {
                    Ok(OpCodeIndex::LeSetExtendedScanResponseData)
                }
                OpCode::LeSetExtendedAdvertisingEnable => {
                    Ok(OpCodeIndex::LeSetExtendedAdvertisingEnable)
                }
                OpCode::LeReadMaximumAdvertisingDataLength => {
                    Ok(OpCodeIndex::LeReadMaximumAdvertisingDataLength)
                }
                OpCode::LeReadNumberOfSupportedAdvertisingSets => {
                    Ok(OpCodeIndex::LeReadNumberOfSupportedAdvertisingSets)
                }
                OpCode::LeRemoveAdvertisingSet => Ok(OpCodeIndex::LeRemoveAdvertisingSet),
                OpCode::LeClearAdvertisingSets => Ok(OpCodeIndex::LeClearAdvertisingSets),
                OpCode::LeSetPeriodicAdvertisingParameters => {
                    Ok(OpCodeIndex::LeSetPeriodicAdvertisingParameters)
                }
                OpCode::LeSetPeriodicAdvertisingData => {
                    Ok(OpCodeIndex::LeSetPeriodicAdvertisingData)
                }
                OpCode::LeSetPeriodicAdvertisingEnable => {
                    Ok(OpCodeIndex::LeSetPeriodicAdvertisingEnable)
                }
                OpCode::LeSetExtendedScanParameters => Ok(OpCodeIndex::LeSetExtendedScanParameters),
                OpCode::LeSetExtendedScanEnable => Ok(OpCodeIndex::LeSetExtendedScanEnable),
                OpCode::LeExtendedCreateConnection => Ok(OpCodeIndex::LeExtendedCreateConnection),
                OpCode::LePeriodicAdvertisingCreateSync => {
                    Ok(OpCodeIndex::LePeriodicAdvertisingCreateSync)
                }
                OpCode::LePeriodicAdvertisingCreateSyncCancel => {
                    Ok(OpCodeIndex::LePeriodicAdvertisingCreateSyncCancel)
                }
                OpCode::LePeriodicAdvertisingTerminateSync => {
                    Ok(OpCodeIndex::LePeriodicAdvertisingTerminateSync)
                }
                OpCode::LeAddDeviceToPeriodicAdvertiserList => {
                    Ok(OpCodeIndex::LeAddDeviceToPeriodicAdvertiserList)
                }
                OpCode::LeRemoveDeviceFromPeriodicAdvertiserList => {
                    Ok(OpCodeIndex::LeRemoveDeviceFromPeriodicAdvertiserList)
                }
                OpCode::LeClearPeriodicAdvertiserList => {
                    Ok(OpCodeIndex::LeClearPeriodicAdvertiserList)
                }
                OpCode::LeReadPeriodicAdvertiserListSize => {
                    Ok(OpCodeIndex::LeReadPeriodicAdvertiserListSize)
                }
                OpCode::LeReadTransmitPower => Ok(OpCodeIndex::LeReadTransmitPower),
                OpCode::LeReadRfPathCompensationPower => {
                    Ok(OpCodeIndex::LeReadRfPathCompensationPower)
                }
                OpCode::LeWriteRfPathCompensationPower => {
                    Ok(OpCodeIndex::LeWriteRfPathCompensationPower)
                }
                OpCode::LeSetPrivacyMode => Ok(OpCodeIndex::LeSetPrivacyMode),
                OpCode::LeReceiverTestV3 => Ok(OpCodeIndex::LeReceiverTestV3),
                OpCode::LeTransmitterTestV3 => Ok(OpCodeIndex::LeTransmitterTestV3),
                OpCode::LeSetConnectionlessCteTransmitParameters => {
                    Ok(OpCodeIndex::LeSetConnectionlessCteTransmitParameters)
                }
                OpCode::LeSetConnectionlessCteTransmitEnable => {
                    Ok(OpCodeIndex::LeSetConnectionlessCteTransmitEnable)
                }
                OpCode::LeSetConnectionlessIqSamplingEnable => {
                    Ok(OpCodeIndex::LeSetConnectionlessIqSamplingEnable)
                }
                OpCode::LeSetConnectionCteReceiveParameters => {
                    Ok(OpCodeIndex::LeSetConnectionCteReceiveParameters)
                }
                OpCode::LeSetConnectionCteTransmitParameters => {
                    Ok(OpCodeIndex::LeSetConnectionCteTransmitParameters)
                }
                OpCode::LeConnectionCteRequestEnable => {
                    Ok(OpCodeIndex::LeConnectionCteRequestEnable)
                }
                OpCode::LeConnectionCteResponseEnable => {
                    Ok(OpCodeIndex::LeConnectionCteResponseEnable)
                }
                OpCode::LeReadAntennaInformation => Ok(OpCodeIndex::LeReadAntennaInformation),
                OpCode::LeSetPeriodicAdvertisingReceiveEnable => {
                    Ok(OpCodeIndex::LeSetPeriodicAdvertisingReceiveEnable)
                }
                OpCode::LePeriodicAdvertisingSyncTransfer => {
                    Ok(OpCodeIndex::LePeriodicAdvertisingSyncTransfer)
                }
                OpCode::LePeriodicAdvertisingSetInfoTransfer => {
                    Ok(OpCodeIndex::LePeriodicAdvertisingSetInfoTransfer)
                }
                OpCode::LeSetPeriodicAdvertisingSyncTransferParameters => {
                    Ok(OpCodeIndex::LeSetPeriodicAdvertisingSyncTransferParameters)
                }
                OpCode::LeSetDefaultPeriodicAdvertisingSyncTransferParameters => {
                    Ok(OpCodeIndex::LeSetDefaultPeriodicAdvertisingSyncTransferParameters)
                }
                OpCode::LeGenerateDhkeyV2 => Ok(OpCodeIndex::LeGenerateDhkeyV2),
                OpCode::ReadLocalSimplePairingOptions => {
                    Ok(OpCodeIndex::ReadLocalSimplePairingOptions)
                }
                OpCode::LeModifySleepClockAccuracy => Ok(OpCodeIndex::LeModifySleepClockAccuracy),
                OpCode::LeReadBufferSizeV2 => Ok(OpCodeIndex::LeReadBufferSizeV2),
                OpCode::LeReadIsoTxSync => Ok(OpCodeIndex::LeReadIsoTxSync),
                OpCode::LeSetCigParameters => Ok(OpCodeIndex::LeSetCigParameters),
                OpCode::LeSetCigParametersTest => Ok(OpCodeIndex::LeSetCigParametersTest),
                OpCode::LeCreateCis => Ok(OpCodeIndex::LeCreateCis),
                OpCode::LeRemoveCig => Ok(OpCodeIndex::LeRemoveCig),
                OpCode::LeAcceptCisRequest => Ok(OpCodeIndex::LeAcceptCisRequest),
                OpCode::LeRejectCisRequest => Ok(OpCodeIndex::LeRejectCisRequest),
                OpCode::LeCreateBig => Ok(OpCodeIndex::LeCreateBig),
                OpCode::LeCreateBigTest => Ok(OpCodeIndex::LeCreateBigTest),
                OpCode::LeTerminateBig => Ok(OpCodeIndex::LeTerminateBig),
                OpCode::LeBigCreateSync => Ok(OpCodeIndex::LeBigCreateSync),
                OpCode::LeBigTerminateSync => Ok(OpCodeIndex::LeBigTerminateSync),
                OpCode::LeRequestPeerSca => Ok(OpCodeIndex::LeRequestPeerSca),
                OpCode::LeSetupIsoDataPath => Ok(OpCodeIndex::LeSetupIsoDataPath),
                OpCode::LeRemoveIsoDataPath => Ok(OpCodeIndex::LeRemoveIsoDataPath),
                OpCode::LeIsoTransmitTest => Ok(OpCodeIndex::LeIsoTransmitTest),
                OpCode::LeIsoReceiveTest => Ok(OpCodeIndex::LeIsoReceiveTest),
                OpCode::LeIsoReadTestCounters => Ok(OpCodeIndex::LeIsoReadTestCounters),
                OpCode::LeIsoTestEnd => Ok(OpCodeIndex::LeIsoTestEnd),
                OpCode::LeSetHostFeature => Ok(OpCodeIndex::LeSetHostFeature),
                OpCode::LeReadIsoLinkQuality => Ok(OpCodeIndex::LeReadIsoLinkQuality),
                OpCode::LeEnhancedReadTransmitPowerLevel => {
                    Ok(OpCodeIndex::LeEnhancedReadTransmitPowerLevel)
                }
                OpCode::LeReadRemoteTransmitPowerLevel => {
                    Ok(OpCodeIndex::LeReadRemoteTransmitPowerLevel)
                }
                OpCode::LeSetPathLossReportingParameters => {
                    Ok(OpCodeIndex::LeSetPathLossReportingParameters)
                }
                OpCode::LeSetPathLossReportingEnable => {
                    Ok(OpCodeIndex::LeSetPathLossReportingEnable)
                }
                OpCode::LeSetTransmitPowerReportingEnable => {
                    Ok(OpCodeIndex::LeSetTransmitPowerReportingEnable)
                }
                OpCode::LeTransmitterTestV4 => Ok(OpCodeIndex::LeTransmitterTestV4),
                OpCode::SetEcosystemBaseInterval => Ok(OpCodeIndex::SetEcosystemBaseInterval),
                OpCode::ReadLocalSupportedCodecsV2 => Ok(OpCodeIndex::ReadLocalSupportedCodecsV2),
                OpCode::ReadLocalSupportedCodecCapabilities => {
                    Ok(OpCodeIndex::ReadLocalSupportedCodecCapabilities)
                }
                OpCode::ReadLocalSupportedControllerDelay => {
                    Ok(OpCodeIndex::ReadLocalSupportedControllerDelay)
                }
                OpCode::ConfigureDataPath => Ok(OpCodeIndex::ConfigureDataPath),
                OpCode::LeSetDataRelatedAddressChanges => {
                    Ok(OpCodeIndex::LeSetDataRelatedAddressChanges)
                }
                OpCode::SetMinEncryptionKeySize => Ok(OpCodeIndex::SetMinEncryptionKeySize),
                OpCode::LeSetDefaultSubrate => Ok(OpCodeIndex::LeSetDefaultSubrate),
                OpCode::LeSubrateRequest => Ok(OpCodeIndex::LeSubrateRequest),
                _ => Err("No mapping for provided key"),
            }
        }
    }
}
