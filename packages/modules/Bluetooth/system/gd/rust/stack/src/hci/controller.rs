//! Loads info from the controller at startup

use crate::hci::{Address, CommandSender};
use bt_packets::hci::{
    Enable, ErrorCode, LeHostFeatureBits, LeMaximumDataLength, LeReadBufferSizeV1Builder,
    LeReadBufferSizeV2Builder, LeReadFilterAcceptListSizeBuilder,
    LeReadLocalSupportedFeaturesBuilder, LeReadMaximumAdvertisingDataLengthBuilder,
    LeReadMaximumDataLengthBuilder, LeReadNumberOfSupportedAdvertisingSetsBuilder,
    LeReadPeriodicAdvertiserListSizeBuilder, LeReadResolvingListSizeBuilder,
    LeReadSuggestedDefaultDataLengthBuilder, LeReadSupportedStatesBuilder, LeSetEventMaskBuilder,
    LeSetHostFeatureBuilder, LocalVersionInformation, OpCode, OpCodeIndex, ReadBdAddrBuilder,
    ReadBufferSizeBuilder, ReadLocalExtendedFeaturesBuilder, ReadLocalNameBuilder,
    ReadLocalSupportedCommandsBuilder, ReadLocalVersionInformationBuilder, SetEventMaskBuilder,
    WriteLeHostSupportBuilder, WriteSimplePairingModeBuilder,
};
use gddi::{module, provides, Stoppable};
use std::convert::TryFrom;
use std::sync::Arc;

module! {
    controller_module,
    providers {
        Arc<ControllerExports> => provide_controller,
    },
}

macro_rules! assert_success {
    ($hci:ident.send($builder:expr)) => {{
        let response = $hci.send($builder).await;
        assert!(response.get_status() == ErrorCode::Success);

        response
    }};
}

#[provides]
async fn provide_controller(mut hci: CommandSender) -> Arc<ControllerExports> {
    assert_success!(hci.send(LeSetEventMaskBuilder { le_event_mask: 0x000000004d021e7f }));
    assert_success!(hci.send(SetEventMaskBuilder { event_mask: 0x3dbfffffffffffff }));
    assert_success!(
        hci.send(WriteSimplePairingModeBuilder { simple_pairing_mode: Enable::Enabled })
    );
    assert_success!(hci.send(WriteLeHostSupportBuilder {
        le_supported_host: Enable::Enabled,
        simultaneous_le_host: Enable::Enabled
    }));

    let name = null_terminated_to_string(
        assert_success!(hci.send(ReadLocalNameBuilder {})).get_local_name(),
    );

    let version_info = assert_success!(hci.send(ReadLocalVersionInformationBuilder {}))
        .get_local_version_information()
        .clone();

    let commands = SupportedCommands {
        supported: *assert_success!(hci.send(ReadLocalSupportedCommandsBuilder {}))
            .get_supported_commands(),
    };

    let features = read_features(&mut hci).await;

    let buffer_size = assert_success!(hci.send(ReadBufferSizeBuilder {}));
    let acl_buffer_length = buffer_size.get_acl_data_packet_length();
    let mut acl_buffers = buffer_size.get_total_num_acl_data_packets();

    let (mut le_buffer_length, mut le_buffers, iso_buffer_length, iso_buffers) =
        if commands.is_supported(OpCode::LeReadBufferSizeV2) {
            let response = assert_success!(hci.send(LeReadBufferSizeV2Builder {}));
            (
                response.get_le_buffer_size().le_data_packet_length,
                response.get_le_buffer_size().total_num_le_packets,
                response.get_iso_buffer_size().le_data_packet_length,
                response.get_iso_buffer_size().total_num_le_packets,
            )
        } else {
            let response = assert_success!(hci.send(LeReadBufferSizeV1Builder {}));
            (
                response.get_le_buffer_size().le_data_packet_length,
                response.get_le_buffer_size().total_num_le_packets,
                0,
                0,
            )
        };

    // If the controller reports zero LE buffers, the ACL buffers are shared between classic & LE
    if le_buffers == 0 {
        le_buffers = (acl_buffers / 2) as u8;
        acl_buffers -= le_buffers as u16;
        le_buffer_length = acl_buffer_length;
    }

    let le_features = SupportedLeFeatures::new(
        assert_success!(hci.send(LeReadLocalSupportedFeaturesBuilder {})).get_le_features(),
    );
    let le_supported_states =
        assert_success!(hci.send(LeReadSupportedStatesBuilder {})).get_le_states();
    let le_connect_list_size = assert_success!(hci.send(LeReadFilterAcceptListSizeBuilder {}))
        .get_filter_accept_list_size();
    let le_resolving_list_size =
        assert_success!(hci.send(LeReadResolvingListSizeBuilder {})).get_resolving_list_size();

    let le_max_data_length = if commands.is_supported(OpCode::LeReadMaximumDataLength) {
        assert_success!(hci.send(LeReadMaximumDataLengthBuilder {}))
            .get_le_maximum_data_length()
            .clone()
    } else {
        LeMaximumDataLength {
            supported_max_rx_octets: 0,
            supported_max_rx_time: 0,
            supported_max_tx_octets: 0,
            supported_max_tx_time: 0,
        }
    };

    let le_suggested_default_data_length =
        if commands.is_supported(OpCode::LeReadSuggestedDefaultDataLength) {
            assert_success!(hci.send(LeReadSuggestedDefaultDataLengthBuilder {})).get_tx_octets()
        } else {
            0
        };

    let le_max_advertising_data_length =
        if commands.is_supported(OpCode::LeReadMaximumAdvertisingDataLength) {
            assert_success!(hci.send(LeReadMaximumAdvertisingDataLengthBuilder {}))
                .get_maximum_advertising_data_length()
        } else {
            31
        };
    let le_supported_advertising_sets =
        if commands.is_supported(OpCode::LeReadNumberOfSupportedAdvertisingSets) {
            assert_success!(hci.send(LeReadNumberOfSupportedAdvertisingSetsBuilder {}))
                .get_number_supported_advertising_sets()
        } else {
            1
        };
    let le_periodic_advertiser_list_size =
        if commands.is_supported(OpCode::LeReadPeriodicAdvertiserListSize) {
            assert_success!(hci.send(LeReadPeriodicAdvertiserListSizeBuilder {}))
                .get_periodic_advertiser_list_size()
        } else {
            0
        };

    if commands.is_supported(OpCode::LeSetHostFeature) {
        assert_success!(hci.send(LeSetHostFeatureBuilder {
            bit_number: LeHostFeatureBits::ConnectedIsoStreamHostSupport,
            bit_value: Enable::Enabled
        }));
    }

    let address = assert_success!(hci.send(ReadBdAddrBuilder {})).get_bd_addr();

    Arc::new(ControllerExports {
        name,
        address,
        version_info,
        commands,
        features,
        acl_buffer_length,
        acl_buffers,
        sco_buffer_length: buffer_size.get_synchronous_data_packet_length(),
        sco_buffers: buffer_size.get_total_num_synchronous_data_packets(),
        le_buffer_length,
        le_buffers,
        iso_buffer_length,
        iso_buffers,
        le_features,
        le_supported_states,
        le_connect_list_size,
        le_resolving_list_size,
        le_max_data_length,
        le_suggested_default_data_length,
        le_max_advertising_data_length,
        le_supported_advertising_sets,
        le_periodic_advertiser_list_size,
    })
}

async fn read_features(hci: &mut CommandSender) -> SupportedFeatures {
    let mut features = Vec::new();
    let mut page_number: u8 = 0;
    let mut max_page_number: u8 = 1;
    while page_number < max_page_number {
        let response = assert_success!(hci.send(ReadLocalExtendedFeaturesBuilder { page_number }));
        max_page_number = response.get_maximum_page_number();
        features.push(response.get_extended_lmp_features());
        page_number += 1;
    }

    SupportedFeatures::new(features)
}

/// Controller interface
#[derive(Clone, Stoppable)]
#[allow(missing_docs)]
pub struct ControllerExports {
    pub name: String,
    pub address: Address,
    pub version_info: LocalVersionInformation,
    pub commands: SupportedCommands,
    pub features: SupportedFeatures,
    pub acl_buffer_length: u16,
    pub acl_buffers: u16,
    pub sco_buffer_length: u8,
    pub sco_buffers: u16,
    pub le_buffer_length: u16,
    pub le_buffers: u8,
    pub iso_buffer_length: u16,
    pub iso_buffers: u8,
    pub le_features: SupportedLeFeatures,
    pub le_supported_states: u64,
    pub le_connect_list_size: u8,
    pub le_resolving_list_size: u8,
    pub le_max_data_length: LeMaximumDataLength,
    pub le_suggested_default_data_length: u16,
    pub le_max_advertising_data_length: u16,
    pub le_supported_advertising_sets: u8,
    pub le_periodic_advertiser_list_size: u8,
}

/// Convenience struct for checking what commands are supported
#[derive(Clone)]
pub struct SupportedCommands {
    supported: [u8; 64],
}

impl SupportedCommands {
    /// Check whether a given opcode is supported by the controller
    pub fn is_supported(&self, opcode: OpCode) -> bool {
        let converted = OpCodeIndex::try_from(opcode);
        if converted.is_err() {
            return false;
        }

        let index = u16::from(converted.unwrap()) as usize;

        // OpCodeIndex is encoded as octet * 10 + bit for readability
        self.supported[index / 10] & (1 << (index % 10)) == 1
    }
}

macro_rules! supported_features {
    ($($id:ident => $page:literal : $bit:literal),*) => {
        /// Convenience struct for checking what features are supported
        #[derive(Clone)]
        #[allow(missing_docs)]
        pub struct SupportedFeatures {
            $(pub $id: bool,)*
        }

        impl SupportedFeatures {
            fn new(supported: Vec<u64>) -> Self {
                #[allow(clippy::get_first)]
                Self {
                    $($id: *supported.get($page).unwrap_or(&0) & (1 << $bit) != 0,)*
                }
            }
        }
    }
}

supported_features! {
    three_slot_packets => 0:0,
    five_slot_packets => 0:1,
    role_switch => 0:5,
    hold_mode => 0:6,
    sniff_mode => 0:7,
    park_mode => 0:8,
    sco => 0:11,
    hv2_packets => 0:12,
    hv3_packets => 0:13,
    classic_2m_phy => 0:25,
    classic_3m_phy => 0:26,
    interlaced_inquiry_scan => 0:28,
    rssi_with_inquiry_results => 0:30,
    ev3_packets => 0:31,
    ev4_packets => 0:32,
    ev5_packets => 0:33,
    ble => 0:38,
    three_slot_edr_packets => 0:39,
    five_slot_edr_packets => 0:40,
    sniff_subrating => 0:41,
    encryption_pause => 0:42,
    esco_2m_phy => 0:45,
    esco_3m_phy => 0:46,
    three_slot_esco_edr_packets => 0:47,
    extended_inquiry_response => 0:48,
    simultaneous_le_bredr => 0:49,
    simple_pairing => 0:51,
    non_flushable_pb => 0:54,
    secure_connections => 2:8
}

macro_rules! supported_le_features {
    ($($id:ident => $bit:literal,)*) => {
        /// Convenience struct for checking what features are supported
        #[derive(Clone)]
        #[allow(missing_docs)]
        pub struct SupportedLeFeatures {
            $(pub $id: bool,)*
        }

        impl SupportedLeFeatures {
            fn new(supported: u64) -> Self {
                Self {
                    $($id: supported & (1 << $bit) != 0,)*
                }
            }
        }
    }
}

supported_le_features! {
    connection_parameter_request => 1,
    connection_parameters_request => 2,
    peripheral_initiated_feature_exchange => 3,
    packet_extension => 5,
    privacy => 6,
    ble_2m_phy => 8,
    ble_coded_phy => 11,
    extended_advertising => 12,
    periodic_advertising => 13,
    periodic_advertising_sync_transfer_sender => 24,
    periodic_advertising_sync_transfer_recipient => 25,
    connected_iso_stream_central => 28,
    connected_iso_stream_peripheral => 29,
    iso_broadcaster => 30,
    synchronized_receiver => 31,
    ble_periodic_advertising_adi => 36,
    ble_connection_subrating => 37,
    ble_connection_subrating_host => 38,
}

/// Convert a null terminated C string into a Rust String
pub fn null_terminated_to_string(slice: &[u8]) -> String {
    let temp = std::str::from_utf8(slice).unwrap();
    temp[0..temp.find('\0').unwrap()].to_string()
}
