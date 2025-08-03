//! D-Bus proxy implementations of the APIs.

use bt_topshim::btif::{
    BtAddrType, BtBondState, BtConnectionState, BtDeviceType, BtDiscMode, BtPropertyType,
    BtSspVariant, BtStatus, BtTransport, BtVendorProductInfo, Uuid, Uuid128Bit,
};
use bt_topshim::profiles::a2dp::{
    A2dpCodecBitsPerSample, A2dpCodecChannelMode, A2dpCodecConfig, A2dpCodecIndex,
    A2dpCodecSampleRate, PresentationPosition,
};
use bt_topshim::profiles::avrcp::PlayerMetadata;
use bt_topshim::profiles::gatt::{AdvertisingStatus, GattStatus, LePhy};
use bt_topshim::profiles::hfp::{EscoCodingFormat, HfpCodecBitId, HfpCodecFormat};
use bt_topshim::profiles::hid_host::BthhReportType;
use bt_topshim::profiles::sdp::{
    BtSdpDipRecord, BtSdpHeaderOverlay, BtSdpMasRecord, BtSdpMnsRecord, BtSdpMpsRecord,
    BtSdpOpsRecord, BtSdpPceRecord, BtSdpPseRecord, BtSdpRecord, BtSdpSapRecord, BtSdpType,
    SupportedDependencies, SupportedFormatsList, SupportedScenarios,
};
use bt_topshim::profiles::socket::SocketType;
use bt_topshim::profiles::ProfileConnectionState;

use btstack::battery_manager::{Battery, BatterySet, IBatteryManager, IBatteryManagerCallback};
use btstack::bluetooth::{
    BluetoothDevice, BtAdapterRole, IBluetooth, IBluetoothCallback, IBluetoothConnectionCallback,
    IBluetoothQALegacy,
};
use btstack::bluetooth_admin::{IBluetoothAdmin, IBluetoothAdminPolicyCallback, PolicyEffect};
use btstack::bluetooth_adv::{
    AdvertiseData, AdvertisingSetParameters, IAdvertisingSetCallback, ManfId,
    PeriodicAdvertisingParameters,
};
use btstack::bluetooth_gatt::{
    BluetoothGattCharacteristic, BluetoothGattDescriptor, BluetoothGattService,
    GattWriteRequestStatus, GattWriteType, IBluetoothGatt, IBluetoothGattCallback,
    IBluetoothGattServerCallback, IScannerCallback, ScanFilter, ScanFilterCondition,
    ScanFilterPattern, ScanResult, ScanSettings, ScanType,
};
use btstack::bluetooth_media::{
    BluetoothAudioDevice, IBluetoothMedia, IBluetoothMediaCallback, IBluetoothTelephony,
    IBluetoothTelephonyCallback,
};
use btstack::bluetooth_qa::IBluetoothQA;
use btstack::socket_manager::{
    BluetoothServerSocket, BluetoothSocket, CallbackId, IBluetoothSocketManager,
    IBluetoothSocketManagerCallbacks, SocketId, SocketResult,
};
use btstack::{RPCProxy, SuspendMode};

use btstack::suspend::{ISuspend, ISuspendCallback, SuspendType};

use dbus::arg::RefArg;
use dbus::nonblock::SyncConnection;

use dbus_projection::prelude::*;

use dbus_macros::{
    dbus_method, dbus_propmap, generate_dbus_exporter, generate_dbus_interface_client,
};

use manager_service::iface_bluetooth_manager::{
    AdapterWithEnabled, IBluetoothManager, IBluetoothManagerCallback,
};

use num_traits::{FromPrimitive, ToPrimitive};

use std::collections::HashMap;
use std::convert::{TryFrom, TryInto};
use std::sync::Arc;

use btstack::bluetooth_qa::IBluetoothQACallback;

use crate::dbus_arg::{DBusArg, DBusArgError, DirectDBus, RefArgToRust};

fn make_object_path(idx: i32, name: &str) -> dbus::Path {
    dbus::Path::new(format!("/org/chromium/bluetooth/hci{}/{}", idx, name)).unwrap()
}

impl_dbus_arg_enum!(AdvertisingStatus);
impl_dbus_arg_enum!(BtBondState);
impl_dbus_arg_enum!(BtConnectionState);
impl_dbus_arg_enum!(BtDeviceType);
impl_dbus_arg_enum!(BtAddrType);
impl_dbus_arg_enum!(BtPropertyType);
impl_dbus_arg_enum!(BtSspVariant);
impl_dbus_arg_enum!(BtStatus);
impl_dbus_arg_enum!(BtTransport);
impl_dbus_arg_enum!(GattStatus);
impl_dbus_arg_enum!(GattWriteRequestStatus);
impl_dbus_arg_enum!(GattWriteType);
impl_dbus_arg_enum!(LePhy);
impl_dbus_arg_enum!(ProfileConnectionState);
impl_dbus_arg_enum!(ScanType);
impl_dbus_arg_enum!(SocketType);
impl_dbus_arg_enum!(SuspendMode);
impl_dbus_arg_enum!(SuspendType);
impl_dbus_arg_from_into!(Uuid, Vec<u8>);
impl_dbus_arg_enum!(BthhReportType);
impl_dbus_arg_enum!(BtAdapterRole);

impl RefArgToRust for Uuid {
    type RustType = Vec<u8>;

    fn ref_arg_to_rust(
        arg: &(dyn dbus::arg::RefArg + 'static),
        name: String,
    ) -> Result<Self::RustType, Box<dyn std::error::Error>> {
        <Vec<u8> as RefArgToRust>::ref_arg_to_rust(arg, name)
    }
}

// Represents Uuid128Bit as an array in D-Bus.
impl DBusArg for Uuid128Bit {
    type DBusType = Vec<u8>;

    fn from_dbus(
        data: Vec<u8>,
        _conn: Option<Arc<SyncConnection>>,
        _remote: Option<dbus::strings::BusName<'static>>,
        _disconnect_watcher: Option<Arc<std::sync::Mutex<DisconnectWatcher>>>,
    ) -> Result<[u8; 16], Box<dyn std::error::Error>> {
        return Ok(data.try_into().unwrap());
    }

    fn to_dbus(data: [u8; 16]) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        return Ok(data.to_vec());
    }

    // We don't log in btclient.
    fn log(_data: &[u8; 16]) -> String {
        String::new()
    }
}

impl_dbus_arg_enum!(BtSdpType);

#[dbus_propmap(BtSdpHeaderOverlay)]
struct BtSdpHeaderOverlayDBus {
    sdp_type: BtSdpType,
    uuid: Uuid,
    service_name_length: u32,
    service_name: String,
    rfcomm_channel_number: i32,
    l2cap_psm: i32,
    profile_version: i32,

    user1_len: i32,
    user1_data: Vec<u8>,
    user2_len: i32,
    user2_data: Vec<u8>,
}

#[dbus_propmap(BtSdpMasRecord)]
struct BtSdpMasRecordDBus {
    hdr: BtSdpHeaderOverlay,
    mas_instance_id: u32,
    supported_features: u32,
    supported_message_types: u32,
}

#[dbus_propmap(BtSdpMnsRecord)]
struct BtSdpMnsRecordDBus {
    hdr: BtSdpHeaderOverlay,
    supported_features: u32,
}

#[dbus_propmap(BtSdpPseRecord)]
struct BtSdpPseRecordDBus {
    hdr: BtSdpHeaderOverlay,
    supported_features: u32,
    supported_repositories: u32,
}

#[dbus_propmap(BtSdpPceRecord)]
struct BtSdpPceRecordDBus {
    hdr: BtSdpHeaderOverlay,
}

impl_dbus_arg_from_into!(SupportedFormatsList, Vec<u8>);

#[dbus_propmap(BtSdpOpsRecord)]
struct BtSdpOpsRecordDBus {
    hdr: BtSdpHeaderOverlay,
    supported_formats_list_len: i32,
    supported_formats_list: SupportedFormatsList,
}

#[dbus_propmap(BtSdpSapRecord)]
struct BtSdpSapRecordDBus {
    hdr: BtSdpHeaderOverlay,
}

#[dbus_propmap(BtSdpDipRecord)]
struct BtSdpDipRecordDBus {
    hdr: BtSdpHeaderOverlay,
    spec_id: u16,
    vendor: u16,
    vendor_id_source: u16,
    product: u16,
    version: u16,
    primary_record: bool,
}

impl_dbus_arg_from_into!(SupportedScenarios, Vec<u8>);
impl_dbus_arg_from_into!(SupportedDependencies, Vec<u8>);

#[dbus_propmap(BtSdpMpsRecord)]
pub struct BtSdpMpsRecordDBus {
    hdr: BtSdpHeaderOverlay,
    supported_scenarios_mpsd: SupportedScenarios,
    supported_scenarios_mpmd: SupportedScenarios,
    supported_dependencies: SupportedDependencies,
}

#[dbus_propmap(BtVendorProductInfo)]
pub struct BtVendorProductInfoDBus {
    vendor_id_src: u8,
    vendor_id: u16,
    product_id: u16,
    version: u16,
}

fn read_propmap_value<T: 'static + DirectDBus>(
    propmap: &dbus::arg::PropMap,
    key: &str,
) -> Result<T, Box<dyn std::error::Error>> {
    let output = propmap
        .get(key)
        .ok_or(Box::new(DBusArgError::new(String::from(format!("Key {} does not exist", key,)))))?;
    let output = <T as RefArgToRust>::ref_arg_to_rust(
        output.as_static_inner(0).ok_or(Box::new(DBusArgError::new(String::from(format!(
            "Unable to convert propmap[\"{}\"] to {}",
            key,
            stringify!(T),
        )))))?,
        String::from(stringify!(T)),
    )?;
    Ok(output)
}

fn parse_propmap_value<T: DBusArg>(
    propmap: &dbus::arg::PropMap,
    key: &str,
) -> Result<T, Box<dyn std::error::Error>>
where
    <T as DBusArg>::DBusType: RefArgToRust<RustType = <T as DBusArg>::DBusType>,
{
    let output = propmap
        .get(key)
        .ok_or(Box::new(DBusArgError::new(String::from(format!("Key {} does not exist", key,)))))?;
    let output = <<T as DBusArg>::DBusType as RefArgToRust>::ref_arg_to_rust(
        output.as_static_inner(0).ok_or(Box::new(DBusArgError::new(String::from(format!(
            "Unable to convert propmap[\"{}\"] to {}",
            key,
            stringify!(T),
        )))))?,
        format!("{}", stringify!(T)),
    )?;
    let output = T::from_dbus(output, None, None, None)?;
    Ok(output)
}

fn write_propmap_value<T: DBusArg>(
    propmap: &mut dbus::arg::PropMap,
    value: T,
    key: &str,
) -> Result<(), Box<dyn std::error::Error>>
where
    T::DBusType: 'static + dbus::arg::RefArg,
{
    propmap.insert(String::from(key), dbus::arg::Variant(Box::new(DBusArg::to_dbus(value)?)));
    Ok(())
}

impl DBusArg for BtSdpRecord {
    type DBusType = dbus::arg::PropMap;
    fn from_dbus(
        data: dbus::arg::PropMap,
        _conn: Option<std::sync::Arc<dbus::nonblock::SyncConnection>>,
        _remote: Option<dbus::strings::BusName<'static>>,
        _disconnect_watcher: Option<
            std::sync::Arc<std::sync::Mutex<dbus_projection::DisconnectWatcher>>,
        >,
    ) -> Result<BtSdpRecord, Box<dyn std::error::Error>> {
        let sdp_type = read_propmap_value::<u32>(&data, &String::from("type"))?;
        let sdp_type = BtSdpType::from(sdp_type);
        let record = match sdp_type {
            BtSdpType::Raw => {
                let arg_0 = parse_propmap_value::<BtSdpHeaderOverlay>(&data, "0")?;
                BtSdpRecord::HeaderOverlay(arg_0)
            }
            BtSdpType::MapMas => {
                let arg_0 = parse_propmap_value::<BtSdpMasRecord>(&data, "0")?;
                BtSdpRecord::MapMas(arg_0)
            }
            BtSdpType::MapMns => {
                let arg_0 = parse_propmap_value::<BtSdpMnsRecord>(&data, "0")?;
                BtSdpRecord::MapMns(arg_0)
            }
            BtSdpType::PbapPse => {
                let arg_0 = parse_propmap_value::<BtSdpPseRecord>(&data, "0")?;
                BtSdpRecord::PbapPse(arg_0)
            }
            BtSdpType::PbapPce => {
                let arg_0 = parse_propmap_value::<BtSdpPceRecord>(&data, "0")?;
                BtSdpRecord::PbapPce(arg_0)
            }
            BtSdpType::OppServer => {
                let arg_0 = parse_propmap_value::<BtSdpOpsRecord>(&data, "0")?;
                BtSdpRecord::OppServer(arg_0)
            }
            BtSdpType::SapServer => {
                let arg_0 = parse_propmap_value::<BtSdpSapRecord>(&data, "0")?;
                BtSdpRecord::SapServer(arg_0)
            }
            BtSdpType::Dip => {
                let arg_0 = parse_propmap_value::<BtSdpDipRecord>(&data, "0")?;
                BtSdpRecord::Dip(arg_0)
            }
            BtSdpType::Mps => {
                let arg_0 = parse_propmap_value::<BtSdpMpsRecord>(&data, "0")?;
                BtSdpRecord::Mps(arg_0)
            }
        };
        Ok(record)
    }

    fn to_dbus(record: BtSdpRecord) -> Result<dbus::arg::PropMap, Box<dyn std::error::Error>> {
        let mut map: dbus::arg::PropMap = std::collections::HashMap::new();
        write_propmap_value::<u32>(
            &mut map,
            BtSdpType::from(&record) as u32,
            &String::from("type"),
        )?;
        match record {
            BtSdpRecord::HeaderOverlay(header) => {
                write_propmap_value::<BtSdpHeaderOverlay>(&mut map, header, &String::from("0"))?
            }
            BtSdpRecord::MapMas(mas_record) => {
                write_propmap_value::<BtSdpMasRecord>(&mut map, mas_record, &String::from("0"))?
            }
            BtSdpRecord::MapMns(mns_record) => {
                write_propmap_value::<BtSdpMnsRecord>(&mut map, mns_record, &String::from("0"))?
            }
            BtSdpRecord::PbapPse(pse_record) => {
                write_propmap_value::<BtSdpPseRecord>(&mut map, pse_record, &String::from("0"))?
            }
            BtSdpRecord::PbapPce(pce_record) => {
                write_propmap_value::<BtSdpPceRecord>(&mut map, pce_record, &String::from("0"))?
            }
            BtSdpRecord::OppServer(ops_record) => {
                write_propmap_value::<BtSdpOpsRecord>(&mut map, ops_record, &String::from("0"))?
            }
            BtSdpRecord::SapServer(sap_record) => {
                write_propmap_value::<BtSdpSapRecord>(&mut map, sap_record, &String::from("0"))?
            }
            BtSdpRecord::Dip(dip_record) => {
                write_propmap_value::<BtSdpDipRecord>(&mut map, dip_record, &String::from("0"))?
            }
            BtSdpRecord::Mps(mps_record) => {
                write_propmap_value::<BtSdpMpsRecord>(&mut map, mps_record, &String::from("0"))?
            }
        }
        Ok(map)
    }

    // We don't log in btclient.
    fn log(_data: &BtSdpRecord) -> String {
        String::new()
    }
}

#[dbus_propmap(BluetoothGattDescriptor)]
pub struct BluetoothGattDescriptorDBus {
    uuid: Uuid128Bit,
    instance_id: i32,
    permissions: i32,
}

#[dbus_propmap(BluetoothGattCharacteristic)]
pub struct BluetoothGattCharacteristicDBus {
    uuid: Uuid128Bit,
    instance_id: i32,
    properties: i32,
    permissions: i32,
    key_size: i32,
    write_type: GattWriteType,
    descriptors: Vec<BluetoothGattDescriptor>,
}

#[dbus_propmap(BluetoothGattService)]
pub struct BluetoothGattServiceDBus {
    pub uuid: Uuid128Bit,
    pub instance_id: i32,
    pub service_type: i32,
    pub characteristics: Vec<BluetoothGattCharacteristic>,
    pub included_services: Vec<BluetoothGattService>,
}

#[dbus_propmap(BluetoothDevice)]
pub struct BluetoothDeviceDBus {
    address: String,
    name: String,
}

#[dbus_propmap(ScanSettings)]
struct ScanSettingsDBus {
    interval: i32,
    window: i32,
    scan_type: ScanType,
}

#[dbus_propmap(ScanFilterPattern)]
struct ScanFilterPatternDBus {
    start_position: u8,
    ad_type: u8,
    content: Vec<u8>,
}

#[dbus_propmap(A2dpCodecConfig)]
pub struct A2dpCodecConfigDBus {
    codec_type: i32,
    codec_priority: i32,
    sample_rate: i32,
    bits_per_sample: i32,
    channel_mode: i32,
    codec_specific_1: i64,
    codec_specific_2: i64,
    codec_specific_3: i64,
    codec_specific_4: i64,
}

impl_dbus_arg_enum!(A2dpCodecIndex);
impl_dbus_arg_from_into!(A2dpCodecSampleRate, i32);
impl_dbus_arg_from_into!(A2dpCodecBitsPerSample, i32);
impl_dbus_arg_from_into!(A2dpCodecChannelMode, i32);

impl_dbus_arg_from_into!(EscoCodingFormat, u8);
impl_dbus_arg_from_into!(HfpCodecBitId, i32);
impl_dbus_arg_from_into!(HfpCodecFormat, i32);

#[dbus_propmap(BluetoothAudioDevice)]
pub struct BluetoothAudioDeviceDBus {
    address: String,
    name: String,
    a2dp_caps: Vec<A2dpCodecConfig>,
    hfp_cap: HfpCodecFormat,
    absolute_volume: bool,
}

// Manually converts enum variant from/into D-Bus.
//
// The ScanFilterCondition enum variant is represented as a D-Bus dictionary with one and only one
// member which key determines which variant it refers to and the value determines the data.
//
// For example, ScanFilterCondition::Patterns(data: Vec<u8>) is represented as:
//     array [
//        dict entry(
//           string "patterns"
//           variant array [ ... ]
//        )
//     ]
//
// And ScanFilterCondition::All is represented as:
//     array [
//        dict entry(
//           string "all"
//           variant string "unit"
//        )
//     ]
//
// If enum variant is used many times, we should find a way to avoid boilerplate.
impl DBusArg for ScanFilterCondition {
    type DBusType = dbus::arg::PropMap;
    fn from_dbus(
        data: dbus::arg::PropMap,
        _conn: Option<std::sync::Arc<dbus::nonblock::SyncConnection>>,
        _remote: Option<dbus::strings::BusName<'static>>,
        _disconnect_watcher: Option<
            std::sync::Arc<std::sync::Mutex<dbus_projection::DisconnectWatcher>>,
        >,
    ) -> Result<ScanFilterCondition, Box<dyn std::error::Error>> {
        let variant = match data.get("patterns") {
            Some(variant) => variant,
            None => {
                return Err(Box::new(DBusArgError::new(String::from(format!(
                    "ScanFilterCondition does not contain any enum variant",
                )))));
            }
        };

        match variant.arg_type() {
            dbus::arg::ArgType::Variant => {}
            _ => {
                return Err(Box::new(DBusArgError::new(String::from(format!(
                    "ScanFilterCondition::Patterns must be a variant",
                )))));
            }
        };

        let patterns =
            <<Vec<ScanFilterPattern> as DBusArg>::DBusType as RefArgToRust>::ref_arg_to_rust(
                variant.as_static_inner(0).unwrap(),
                format!("ScanFilterCondition::Patterns"),
            )?;

        let patterns = Vec::<ScanFilterPattern>::from_dbus(patterns, None, None, None)?;
        return Ok(ScanFilterCondition::Patterns(patterns));
    }

    fn to_dbus(
        condition: ScanFilterCondition,
    ) -> Result<dbus::arg::PropMap, Box<dyn std::error::Error>> {
        let mut map: dbus::arg::PropMap = std::collections::HashMap::new();
        match condition {
            ScanFilterCondition::Patterns(patterns) => {
                map.insert(
                    String::from("patterns"),
                    dbus::arg::Variant(Box::new(DBusArg::to_dbus(patterns)?)),
                );
            }
            _ => {}
        }
        return Ok(map);
    }

    // We don't log in btclient.
    fn log(_data: &ScanFilterCondition) -> String {
        String::new()
    }
}

#[dbus_propmap(ScanFilter)]
struct ScanFilterDBus {
    rssi_high_threshold: u8,
    rssi_low_threshold: u8,
    rssi_low_timeout: u8,
    rssi_sampling_period: u8,
    condition: ScanFilterCondition,
}

#[dbus_propmap(ScanResult)]
struct ScanResultDBus {
    name: String,
    address: String,
    addr_type: u8,
    event_type: u16,
    primary_phy: u8,
    secondary_phy: u8,
    advertising_sid: u8,
    tx_power: i8,
    rssi: i8,
    periodic_adv_int: u16,
    flags: u8,
    service_uuids: Vec<Uuid128Bit>,
    service_data: HashMap<String, Vec<u8>>,
    manufacturer_data: HashMap<u16, Vec<u8>>,
    adv_data: Vec<u8>,
}

#[dbus_propmap(PresentationPosition)]
struct PresentationPositionDBus {
    remote_delay_report_ns: u64,
    total_bytes_read: u64,
    data_position_sec: i64,
    data_position_nsec: i32,
}

#[dbus_propmap(PlayerMetadata)]
struct PlayerMetadataDBus {
    title: String,
    artist: String,
    album: String,
    length_us: i64,
}

struct IBluetoothCallbackDBus {}

impl RPCProxy for IBluetoothCallbackDBus {}

#[generate_dbus_exporter(
    export_bluetooth_callback_dbus_intf,
    "org.chromium.bluetooth.BluetoothCallback"
)]
impl IBluetoothCallback for IBluetoothCallbackDBus {
    #[dbus_method("OnAdapterPropertyChanged", DBusLog::Disable)]
    fn on_adapter_property_changed(&mut self, prop: BtPropertyType) {}

    #[dbus_method("OnDevicePropertiesChanged", DBusLog::Disable)]
    fn on_device_properties_changed(
        &mut self,
        remote_device: BluetoothDevice,
        props: Vec<BtPropertyType>,
    ) {
    }

    #[dbus_method("OnAddressChanged", DBusLog::Disable)]
    fn on_address_changed(&mut self, addr: String) {}

    #[dbus_method("OnNameChanged", DBusLog::Disable)]
    fn on_name_changed(&mut self, name: String) {}

    #[dbus_method("OnDiscoverableChanged", DBusLog::Disable)]
    fn on_discoverable_changed(&mut self, discoverable: bool) {}

    #[dbus_method("OnDeviceFound", DBusLog::Disable)]
    fn on_device_found(&mut self, remote_device: BluetoothDevice) {}

    #[dbus_method("OnDeviceCleared", DBusLog::Disable)]
    fn on_device_cleared(&mut self, remote_device: BluetoothDevice) {}

    #[dbus_method("OnDiscoveringChanged", DBusLog::Disable)]
    fn on_discovering_changed(&mut self, discovering: bool) {}

    #[dbus_method("OnSspRequest", DBusLog::Disable)]
    fn on_ssp_request(
        &mut self,
        remote_device: BluetoothDevice,
        cod: u32,
        variant: BtSspVariant,
        passkey: u32,
    ) {
    }

    #[dbus_method("OnPinRequest", DBusLog::Disable)]
    fn on_pin_request(&mut self, remote_device: BluetoothDevice, cod: u32, min_16_digit: bool) {}

    #[dbus_method("OnPinDisplay", DBusLog::Disable)]
    fn on_pin_display(&mut self, remote_device: BluetoothDevice, pincode: String) {}

    #[dbus_method("OnBondStateChanged", DBusLog::Disable)]
    fn on_bond_state_changed(&mut self, status: u32, address: String, state: u32) {}

    #[dbus_method("OnSdpSearchComplete", DBusLog::Disable)]
    fn on_sdp_search_complete(
        &mut self,
        remote_device: BluetoothDevice,
        searched_uuid: Uuid128Bit,
        sdp_records: Vec<BtSdpRecord>,
    ) {
    }

    #[dbus_method("OnSdpRecordCreated", DBusLog::Disable)]
    fn on_sdp_record_created(&mut self, record: BtSdpRecord, handle: i32) {}
}

struct IBluetoothConnectionCallbackDBus {}

impl RPCProxy for IBluetoothConnectionCallbackDBus {}

#[generate_dbus_exporter(
    export_bluetooth_connection_callback_dbus_intf,
    "org.chromium.bluetooth.BluetoothConnectionCallback"
)]
impl IBluetoothConnectionCallback for IBluetoothConnectionCallbackDBus {
    #[dbus_method("OnDeviceConnected", DBusLog::Disable)]
    fn on_device_connected(&mut self, remote_device: BluetoothDevice) {}

    #[dbus_method("OnDeviceDisconnected", DBusLog::Disable)]
    fn on_device_disconnected(&mut self, remote_device: BluetoothDevice) {}
}

struct IScannerCallbackDBus {}

impl RPCProxy for IScannerCallbackDBus {}

#[generate_dbus_exporter(
    export_scanner_callback_dbus_intf,
    "org.chromium.bluetooth.ScannerCallback"
)]
impl IScannerCallback for IScannerCallbackDBus {
    #[dbus_method("OnScannerRegistered", DBusLog::Disable)]
    fn on_scanner_registered(&mut self, uuid: Uuid128Bit, scanner_id: u8, status: GattStatus) {
        dbus_generated!()
    }

    #[dbus_method("OnScanResult", DBusLog::Disable)]
    fn on_scan_result(&mut self, scan_result: ScanResult) {
        dbus_generated!()
    }

    #[dbus_method("OnAdvertisementFound", DBusLog::Disable)]
    fn on_advertisement_found(&mut self, scanner_id: u8, scan_result: ScanResult) {
        dbus_generated!()
    }

    #[dbus_method("OnAdvertisementLost", DBusLog::Disable)]
    fn on_advertisement_lost(&mut self, scanner_id: u8, scan_result: ScanResult) {
        dbus_generated!()
    }

    #[dbus_method("OnSuspendModeChange", DBusLog::Disable)]
    fn on_suspend_mode_change(&mut self, suspend_mode: SuspendMode) {
        dbus_generated!()
    }
}

impl_dbus_arg_enum!(BtDiscMode);

// Implements RPC-friendly wrapper methods for calling IBluetooth, generated by
// `generate_dbus_interface_client` below.
pub(crate) struct BluetoothDBusRPC {
    client_proxy: ClientDBusProxy,
}

pub(crate) struct BluetoothDBus {
    client_proxy: ClientDBusProxy,
    pub rpc: BluetoothDBusRPC,
}

impl BluetoothDBus {
    fn make_client_proxy(conn: Arc<SyncConnection>, index: i32) -> ClientDBusProxy {
        ClientDBusProxy::new(
            conn.clone(),
            String::from("org.chromium.bluetooth"),
            make_object_path(index, "adapter"),
            String::from("org.chromium.bluetooth.Bluetooth"),
        )
    }

    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> BluetoothDBus {
        BluetoothDBus {
            client_proxy: Self::make_client_proxy(conn.clone(), index),
            rpc: BluetoothDBusRPC { client_proxy: Self::make_client_proxy(conn.clone(), index) },
        }
    }
}

#[generate_dbus_interface_client(BluetoothDBusRPC)]
impl IBluetooth for BluetoothDBus {
    #[dbus_method("RegisterCallback")]
    fn register_callback(&mut self, callback: Box<dyn IBluetoothCallback + Send>) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("UnregisterCallback")]
    fn unregister_callback(&mut self, id: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("RegisterConnectionCallback")]
    fn register_connection_callback(
        &mut self,
        callback: Box<dyn IBluetoothConnectionCallback + Send>,
    ) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("UnregisterConnectionCallback")]
    fn unregister_connection_callback(&mut self, id: u32) -> bool {
        dbus_generated!()
    }

    fn init(&mut self, _init_flags: Vec<String>) -> bool {
        // Not implemented by server
        true
    }

    fn enable(&mut self) -> bool {
        // Not implemented by server
        true
    }

    fn disable(&mut self) -> bool {
        // Not implemented by server
        true
    }

    fn cleanup(&mut self) {
        // Not implemented by server
    }

    #[dbus_method("GetAddress")]
    fn get_address(&self) -> String {
        dbus_generated!()
    }

    #[dbus_method("GetUuids")]
    fn get_uuids(&self) -> Vec<Uuid128Bit> {
        dbus_generated!()
    }

    #[dbus_method("GetName")]
    fn get_name(&self) -> String {
        dbus_generated!()
    }

    #[dbus_method("SetName")]
    fn set_name(&self, name: String) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetBluetoothClass")]
    fn get_bluetooth_class(&self) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("SetBluetoothClass")]
    fn set_bluetooth_class(&self, cod: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetDiscoverable")]
    fn get_discoverable(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetDiscoverableTimeout")]
    fn get_discoverable_timeout(&self) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("SetDiscoverable")]
    fn set_discoverable(&mut self, mode: BtDiscMode, duration: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("IsMultiAdvertisementSupported")]
    fn is_multi_advertisement_supported(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("IsLeExtendedAdvertisingSupported")]
    fn is_le_extended_advertising_supported(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("StartDiscovery")]
    fn start_discovery(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("CancelDiscovery")]
    fn cancel_discovery(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("IsDiscovering")]
    fn is_discovering(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetDiscoveryEndMillis")]
    fn get_discovery_end_millis(&self) -> u64 {
        dbus_generated!()
    }

    #[dbus_method("CreateBond")]
    fn create_bond(&mut self, device: BluetoothDevice, transport: BtTransport) -> bool {
        dbus_generated!()
    }

    #[dbus_method("CancelBondProcess")]
    fn cancel_bond_process(&mut self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("RemoveBond")]
    fn remove_bond(&mut self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetBondedDevices")]
    fn get_bonded_devices(&self) -> Vec<BluetoothDevice> {
        dbus_generated!()
    }

    #[dbus_method("GetBondState")]
    fn get_bond_state(&self, device: BluetoothDevice) -> BtBondState {
        dbus_generated!()
    }

    #[dbus_method("SetPin")]
    fn set_pin(&self, device: BluetoothDevice, accept: bool, pin_code: Vec<u8>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetPasskey")]
    fn set_passkey(&self, device: BluetoothDevice, accept: bool, passkey: Vec<u8>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetPairingConfirmation")]
    fn set_pairing_confirmation(&self, device: BluetoothDevice, accept: bool) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteName")]
    fn get_remote_name(&self, device: BluetoothDevice) -> String {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteType")]
    fn get_remote_type(&self, device: BluetoothDevice) -> BtDeviceType {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteAlias")]
    fn get_remote_alias(&self, device: BluetoothDevice) -> String {
        dbus_generated!()
    }

    #[dbus_method("SetRemoteAlias")]
    fn set_remote_alias(&mut self, device: BluetoothDevice, new_alias: String) {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteClass")]
    fn get_remote_class(&self, device: BluetoothDevice) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteAppearance")]
    fn get_remote_appearance(&self, device: BluetoothDevice) -> u16 {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteConnected")]
    fn get_remote_connected(&self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteWakeAllowed")]
    fn get_remote_wake_allowed(&self, _device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteVendorProductInfo")]
    fn get_remote_vendor_product_info(&self, _device: BluetoothDevice) -> BtVendorProductInfo {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteAddressType")]
    fn get_remote_address_type(&self, device: BluetoothDevice) -> BtAddrType {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteRSSI")]
    fn get_remote_rssi(&self, device: BluetoothDevice) -> i8 {
        dbus_generated!()
    }

    #[dbus_method("GetConnectedDevices")]
    fn get_connected_devices(&self) -> Vec<BluetoothDevice> {
        dbus_generated!()
    }

    #[dbus_method("GetConnectionState")]
    fn get_connection_state(&self, device: BluetoothDevice) -> BtConnectionState {
        dbus_generated!()
    }

    #[dbus_method("GetProfileConnectionState")]
    fn get_profile_connection_state(&self, profile: Uuid128Bit) -> ProfileConnectionState {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteUuids")]
    fn get_remote_uuids(&self, device: BluetoothDevice) -> Vec<Uuid128Bit> {
        dbus_generated!()
    }

    #[dbus_method("FetchRemoteUuids")]
    fn fetch_remote_uuids(&self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SdpSearch")]
    fn sdp_search(&self, device: BluetoothDevice, uuid: Uuid128Bit) -> bool {
        dbus_generated!()
    }

    #[dbus_method("CreateSdpRecord")]
    fn create_sdp_record(&mut self, sdp_record: BtSdpRecord) -> bool {
        dbus_generated!()
    }

    #[dbus_method("RemoveSdpRecord")]
    fn remove_sdp_record(&self, handle: i32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("ConnectAllEnabledProfiles")]
    fn connect_all_enabled_profiles(&mut self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("DisconnectAllEnabledProfiles")]
    fn disconnect_all_enabled_profiles(&mut self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("IsWbsSupported")]
    fn is_wbs_supported(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("IsSwbSupported")]
    fn is_swb_supported(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetSupportedRoles")]
    fn get_supported_roles(&self) -> Vec<BtAdapterRole> {
        dbus_generated!()
    }

    #[dbus_method("IsCodingFormatSupported")]
    fn is_coding_format_supported(&self, coding_format: EscoCodingFormat) -> bool {
        dbus_generated!()
    }
}

pub(crate) struct BluetoothQALegacyDBus {
    client_proxy: ClientDBusProxy,
}

impl BluetoothQALegacyDBus {
    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> BluetoothQALegacyDBus {
        BluetoothQALegacyDBus {
            client_proxy: ClientDBusProxy::new(
                conn.clone(),
                String::from("org.chromium.bluetooth"),
                make_object_path(index, "adapter"),
                String::from("org.chromium.bluetooth.BluetoothQALegacy"),
            ),
        }
    }
}

#[generate_dbus_interface_client]
impl IBluetoothQALegacy for BluetoothQALegacyDBus {
    #[dbus_method("GetConnectable")]
    fn get_connectable(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetConnectable")]
    fn set_connectable(&mut self, mode: bool) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetAlias")]
    fn get_alias(&self) -> String {
        dbus_generated!()
    }

    #[dbus_method("GetModalias")]
    fn get_modalias(&self) -> String {
        dbus_generated!()
    }

    #[dbus_method("GetHIDReport")]
    fn get_hid_report(
        &mut self,
        addr: String,
        report_type: BthhReportType,
        report_id: u8,
    ) -> BtStatus {
        dbus_generated!()
    }

    #[dbus_method("SetHIDReport")]
    fn set_hid_report(
        &mut self,
        addr: String,
        report_type: BthhReportType,
        report: String,
    ) -> BtStatus {
        dbus_generated!()
    }

    #[dbus_method("SendHIDData")]
    fn send_hid_data(&mut self, addr: String, data: String) -> BtStatus;
}

#[dbus_propmap(AdapterWithEnabled)]
pub struct AdapterWithEnabledDbus {
    hci_interface: i32,
    enabled: bool,
}

// Implements RPC-friendly wrapper methods for calling IBluetoothManager, generated by
// `generate_dbus_interface_client` below.
pub(crate) struct BluetoothManagerDBusRPC {
    client_proxy: ClientDBusProxy,
}

pub(crate) struct BluetoothManagerDBus {
    client_proxy: ClientDBusProxy,
    pub rpc: BluetoothManagerDBusRPC,
}

impl BluetoothManagerDBus {
    fn make_client_proxy(conn: Arc<SyncConnection>) -> ClientDBusProxy {
        ClientDBusProxy::new(
            conn,
            String::from("org.chromium.bluetooth.Manager"),
            dbus::Path::new("/org/chromium/bluetooth/Manager").unwrap(),
            String::from("org.chromium.bluetooth.Manager"),
        )
    }

    pub(crate) fn new(conn: Arc<SyncConnection>) -> BluetoothManagerDBus {
        BluetoothManagerDBus {
            client_proxy: Self::make_client_proxy(conn.clone()),
            rpc: BluetoothManagerDBusRPC { client_proxy: Self::make_client_proxy(conn.clone()) },
        }
    }

    pub(crate) fn is_valid(&self) -> bool {
        let result: Result<(bool,), _> = self.client_proxy.method_withresult("GetFlossEnabled", ());
        return result.is_ok();
    }
}

#[generate_dbus_interface_client(BluetoothManagerDBusRPC)]
impl IBluetoothManager for BluetoothManagerDBus {
    #[dbus_method("Start")]
    fn start(&mut self, hci_interface: i32) {
        dbus_generated!()
    }

    #[dbus_method("Stop")]
    fn stop(&mut self, hci_interface: i32) {
        dbus_generated!()
    }

    #[dbus_method("GetAdapterEnabled")]
    fn get_adapter_enabled(&mut self, hci_interface: i32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("RegisterCallback")]
    fn register_callback(&mut self, callback: Box<dyn IBluetoothManagerCallback + Send>) {
        dbus_generated!()
    }

    #[dbus_method("GetFlossEnabled")]
    fn get_floss_enabled(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetFlossEnabled")]
    fn set_floss_enabled(&mut self, enabled: bool) {
        dbus_generated!()
    }

    #[dbus_method("GetAvailableAdapters")]
    fn get_available_adapters(&mut self) -> Vec<AdapterWithEnabled> {
        dbus_generated!()
    }

    #[dbus_method("GetDefaultAdapter")]
    fn get_default_adapter(&mut self) -> i32 {
        dbus_generated!()
    }

    #[dbus_method("SetDesiredDefaultAdapter")]
    fn set_desired_default_adapter(&mut self, adapter: i32) {
        dbus_generated!()
    }

    #[dbus_method("GetFlossApiVersion")]
    fn get_floss_api_version(&mut self) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("SetTabletMode")]
    fn set_tablet_mode(&mut self, tablet_mode: bool) {
        dbus_generated!()
    }
}

struct IBluetoothManagerCallbackDBus {}

impl RPCProxy for IBluetoothManagerCallbackDBus {}

#[generate_dbus_exporter(
    export_bluetooth_manager_callback_dbus_intf,
    "org.chromium.bluetooth.ManagerCallback"
)]
impl IBluetoothManagerCallback for IBluetoothManagerCallbackDBus {
    #[dbus_method("OnHciDeviceChanged", DBusLog::Disable)]
    fn on_hci_device_changed(&mut self, hci_interface: i32, present: bool) {}

    #[dbus_method("OnHciEnabledChanged", DBusLog::Disable)]
    fn on_hci_enabled_changed(&mut self, hci_interface: i32, enabled: bool) {}

    #[dbus_method("OnDefaultAdapterChanged", DBusLog::Disable)]
    fn on_default_adapter_changed(&mut self, hci_interface: i32) {}
}

#[allow(dead_code)]
struct IAdvertisingSetCallbackDBus {}

impl RPCProxy for IAdvertisingSetCallbackDBus {}

#[generate_dbus_exporter(
    export_advertising_set_callback_dbus_intf,
    "org.chromium.bluetooth.AdvertisingSetCallback"
)]
impl IAdvertisingSetCallback for IAdvertisingSetCallbackDBus {
    #[dbus_method("OnAdvertisingSetStarted", DBusLog::Disable)]
    fn on_advertising_set_started(
        &mut self,
        reg_id: i32,
        advertiser_id: i32,
        tx_power: i32,
        status: AdvertisingStatus,
    ) {
    }

    #[dbus_method("OnOwnAddressRead", DBusLog::Disable)]
    fn on_own_address_read(&mut self, advertiser_id: i32, address_type: i32, address: String) {}

    #[dbus_method("OnAdvertisingSetStopped", DBusLog::Disable)]
    fn on_advertising_set_stopped(&mut self, advertiser_id: i32) {}

    #[dbus_method("OnAdvertisingEnabled", DBusLog::Disable)]
    fn on_advertising_enabled(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        status: AdvertisingStatus,
    ) {
    }

    #[dbus_method("OnAdvertisingDataSet", DBusLog::Disable)]
    fn on_advertising_data_set(&mut self, advertiser_id: i32, status: AdvertisingStatus) {}

    #[dbus_method("OnScanResponseDataSet", DBusLog::Disable)]
    fn on_scan_response_data_set(&mut self, advertiser_id: i32, status: AdvertisingStatus) {}

    #[dbus_method("OnAdvertisingParametersUpdated", DBusLog::Disable)]
    fn on_advertising_parameters_updated(
        &mut self,
        advertiser_id: i32,
        tx_power: i32,
        status: AdvertisingStatus,
    ) {
    }

    #[dbus_method("OnPeriodicAdvertisingParametersUpdated", DBusLog::Disable)]
    fn on_periodic_advertising_parameters_updated(
        &mut self,
        advertiser_id: i32,
        status: AdvertisingStatus,
    ) {
    }

    #[dbus_method("OnPeriodicAdvertisingDataSet", DBusLog::Disable)]
    fn on_periodic_advertising_data_set(&mut self, advertiser_id: i32, status: AdvertisingStatus) {}

    #[dbus_method("OnPeriodicAdvertisingEnabled", DBusLog::Disable)]
    fn on_periodic_advertising_enabled(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        status: AdvertisingStatus,
    ) {
    }

    #[dbus_method("OnSuspendModeChange", DBusLog::Disable)]
    fn on_suspend_mode_change(&mut self, suspend_mode: SuspendMode) {}
}

#[dbus_propmap(AdvertisingSetParameters)]
struct AdvertisingSetParametersDBus {
    connectable: bool,
    scannable: bool,
    is_legacy: bool,
    is_anonymous: bool,
    include_tx_power: bool,
    primary_phy: LePhy,
    secondary_phy: LePhy,
    interval: i32,
    tx_power_level: i32,
    own_address_type: i32,
}

#[dbus_propmap(AdvertiseData)]
pub struct AdvertiseDataDBus {
    service_uuids: Vec<Uuid>,
    solicit_uuids: Vec<Uuid>,
    transport_discovery_data: Vec<Vec<u8>>,
    manufacturer_data: HashMap<ManfId, Vec<u8>>,
    service_data: HashMap<String, Vec<u8>>,
    include_tx_power_level: bool,
    include_device_name: bool,
}

#[dbus_propmap(PeriodicAdvertisingParameters)]
pub struct PeriodicAdvertisingParametersDBus {
    pub include_tx_power: bool,
    pub interval: i32,
}

pub(crate) struct BluetoothAdminDBusRPC {
    client_proxy: ClientDBusProxy,
}

pub(crate) struct BluetoothAdminDBus {
    client_proxy: ClientDBusProxy,
    pub rpc: BluetoothAdminDBusRPC,
}

impl BluetoothAdminDBus {
    fn make_client_proxy(conn: Arc<SyncConnection>, index: i32) -> ClientDBusProxy {
        ClientDBusProxy::new(
            conn,
            String::from("org.chromium.bluetooth"),
            make_object_path(index, "admin"),
            String::from("org.chromium.bluetooth.BluetoothAdmin"),
        )
    }

    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> BluetoothAdminDBus {
        BluetoothAdminDBus {
            client_proxy: Self::make_client_proxy(conn.clone(), index),
            rpc: BluetoothAdminDBusRPC {
                client_proxy: Self::make_client_proxy(conn.clone(), index),
            },
        }
    }
}

#[generate_dbus_interface_client(BluetoothAdminDBusRPC)]
impl IBluetoothAdmin for BluetoothAdminDBus {
    #[dbus_method("IsServiceAllowed")]
    fn is_service_allowed(&self, uuid: Uuid128Bit) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetAllowedServices")]
    fn set_allowed_services(&mut self, services: Vec<Uuid128Bit>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetAllowedServices")]
    fn get_allowed_services(&self) -> Vec<Uuid128Bit> {
        dbus_generated!()
    }

    #[dbus_method("GetDevicePolicyEffect")]
    fn get_device_policy_effect(&self, device: BluetoothDevice) -> Option<PolicyEffect> {
        dbus_generated!()
    }

    #[dbus_method("RegisterAdminPolicyCallback")]
    fn register_admin_policy_callback(
        &mut self,
        callback: Box<dyn IBluetoothAdminPolicyCallback + Send>,
    ) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("UnregisterAdminPolicyCallback")]
    fn unregister_admin_policy_callback(&mut self, callback_id: u32) -> bool {
        dbus_generated!()
    }
}

#[dbus_propmap(PolicyEffect)]
pub struct PolicyEffectDBus {
    pub service_blocked: Vec<Uuid128Bit>,
    pub affected: bool,
}

struct IBluetoothAdminPolicyCallbackDBus {}

impl RPCProxy for IBluetoothAdminPolicyCallbackDBus {}

#[generate_dbus_exporter(
    export_admin_policy_callback_dbus_intf,
    "org.chromium.bluetooth.AdminPolicyCallback"
)]
impl IBluetoothAdminPolicyCallback for IBluetoothAdminPolicyCallbackDBus {
    #[dbus_method("OnServiceAllowlistChanged", DBusLog::Disable)]
    fn on_service_allowlist_changed(&mut self, allowed_list: Vec<Uuid128Bit>) {
        dbus_generated!()
    }

    #[dbus_method("OnDevicePolicyEffectChanged", DBusLog::Disable)]
    fn on_device_policy_effect_changed(
        &mut self,
        device: BluetoothDevice,
        new_policy_effect: Option<PolicyEffect>,
    ) {
        dbus_generated!()
    }
}

pub(crate) struct BluetoothGattDBusRPC {
    client_proxy: ClientDBusProxy,
}

pub(crate) struct BluetoothGattDBus {
    client_proxy: ClientDBusProxy,
    pub rpc: BluetoothGattDBusRPC,
}

impl BluetoothGattDBus {
    fn make_client_proxy(conn: Arc<SyncConnection>, index: i32) -> ClientDBusProxy {
        ClientDBusProxy::new(
            conn,
            String::from("org.chromium.bluetooth"),
            make_object_path(index, "gatt"),
            String::from("org.chromium.bluetooth.BluetoothGatt"),
        )
    }

    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> BluetoothGattDBus {
        BluetoothGattDBus {
            client_proxy: Self::make_client_proxy(conn.clone(), index),
            rpc: BluetoothGattDBusRPC {
                client_proxy: Self::make_client_proxy(conn.clone(), index),
            },
        }
    }
}

#[generate_dbus_interface_client(BluetoothGattDBusRPC)]
impl IBluetoothGatt for BluetoothGattDBus {
    // Scanning

    #[dbus_method("IsMsftSupported")]
    fn is_msft_supported(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("RegisterScannerCallback")]
    fn register_scanner_callback(&mut self, _callback: Box<dyn IScannerCallback + Send>) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("UnregisterScannerCallback")]
    fn unregister_scanner_callback(&mut self, _callback_id: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("RegisterScanner")]
    fn register_scanner(&mut self, callback_id: u32) -> Uuid128Bit {
        dbus_generated!()
    }

    #[dbus_method("UnregisterScanner")]
    fn unregister_scanner(&mut self, scanner_id: u8) -> bool {
        dbus_generated!()
    }

    #[dbus_method("StartScan")]
    fn start_scan(
        &mut self,
        _scanner_id: u8,
        _settings: Option<ScanSettings>,
        _filter: Option<ScanFilter>,
    ) -> BtStatus {
        dbus_generated!()
    }

    #[dbus_method("StopScan")]
    fn stop_scan(&mut self, _scanner_id: u8) -> BtStatus {
        dbus_generated!()
    }

    #[dbus_method("GetScanSuspendMode")]
    fn get_scan_suspend_mode(&self) -> SuspendMode {
        dbus_generated!()
    }

    // Advertising
    #[dbus_method("RegisterAdvertiserCallback")]
    fn register_advertiser_callback(
        &mut self,
        callback: Box<dyn IAdvertisingSetCallback + Send>,
    ) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("UnregisterAdvertiserCallback")]
    fn unregister_advertiser_callback(&mut self, callback_id: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("StartAdvertisingSet")]
    fn start_advertising_set(
        &mut self,
        parameters: AdvertisingSetParameters,
        advertise_data: AdvertiseData,
        scan_response: Option<AdvertiseData>,
        periodic_parameters: Option<PeriodicAdvertisingParameters>,
        periodic_data: Option<AdvertiseData>,
        duration: i32,
        max_ext_adv_events: i32,
        callback_id: u32,
    ) -> i32 {
        dbus_generated!()
    }

    #[dbus_method("StopAdvertisingSet")]
    fn stop_advertising_set(&mut self, advertiser_id: i32) {
        dbus_generated!()
    }

    #[dbus_method("GetOwnAddress")]
    fn get_own_address(&mut self, advertiser_id: i32) {
        dbus_generated!()
    }

    #[dbus_method("EnableAdvertisingSet")]
    fn enable_advertising_set(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        duration: i32,
        max_ext_adv_events: i32,
    ) {
        dbus_generated!()
    }

    #[dbus_method("SetAdvertisingData")]
    fn set_advertising_data(&mut self, advertiser_id: i32, data: AdvertiseData) {
        dbus_generated!()
    }

    #[dbus_method("SetRawAdvertisingData")]
    fn set_raw_adv_data(&mut self, advertiser_id: i32, data: Vec<u8>) {
        dbus_generated!()
    }

    #[dbus_method("SetScanResponseData")]
    fn set_scan_response_data(&mut self, advertiser_id: i32, data: AdvertiseData) {
        dbus_generated!()
    }

    #[dbus_method("SetAdvertisingParameters")]
    fn set_advertising_parameters(
        &mut self,
        advertiser_id: i32,
        parameters: AdvertisingSetParameters,
    ) {
        dbus_generated!()
    }

    #[dbus_method("SetPeriodicAdvertisingParameters")]
    fn set_periodic_advertising_parameters(
        &mut self,
        advertiser_id: i32,
        parameters: PeriodicAdvertisingParameters,
    ) {
        dbus_generated!()
    }

    #[dbus_method("SetPeriodicAdvertisingData")]
    fn set_periodic_advertising_data(&mut self, advertiser_id: i32, data: AdvertiseData) {
        dbus_generated!()
    }

    /// Enable/Disable periodic advertising of the advertising set.
    #[dbus_method("SetPeriodicAdvertisingEnable")]
    fn set_periodic_advertising_enable(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        include_adi: bool,
    ) {
        dbus_generated!()
    }

    // GATT Client

    #[dbus_method("RegisterClient")]
    fn register_client(
        &mut self,
        app_uuid: String,
        callback: Box<dyn IBluetoothGattCallback + Send>,
        eatt_support: bool,
    ) {
        dbus_generated!()
    }

    #[dbus_method("UnregisterClient")]
    fn unregister_client(&mut self, client_id: i32) {
        dbus_generated!()
    }

    #[dbus_method("ClientConnect")]
    fn client_connect(
        &self,
        client_id: i32,
        addr: String,
        is_direct: bool,
        transport: BtTransport,
        opportunistic: bool,
        phy: LePhy,
    ) {
        dbus_generated!()
    }

    #[dbus_method("ClientDisconnect")]
    fn client_disconnect(&self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("ClientSetPreferredPhy")]
    fn client_set_preferred_phy(
        &self,
        client_id: i32,
        addr: String,
        tx_phy: LePhy,
        rx_phy: LePhy,
        phy_options: i32,
    ) {
        dbus_generated!()
    }

    #[dbus_method("ClientReadPhy")]
    fn client_read_phy(&mut self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("RefreshDevice")]
    fn refresh_device(&self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("DiscoverServices")]
    fn discover_services(&self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("DiscoverServiceByUuid")]
    fn discover_service_by_uuid(&self, client_id: i32, addr: String, uuid: String) {
        dbus_generated!()
    }

    #[dbus_method("BtifGattcDiscoverServiceByUuid")]
    fn btif_gattc_discover_service_by_uuid(&self, client_id: i32, addr: String, uuid: String) {
        dbus_generated!()
    }

    #[dbus_method("ReadCharacteristic")]
    fn read_characteristic(&self, client_id: i32, addr: String, handle: i32, auth_req: i32) {
        dbus_generated!()
    }

    #[dbus_method("ReadUsingCharacteristicUuid")]
    fn read_using_characteristic_uuid(
        &self,
        client_id: i32,
        addr: String,
        uuid: String,
        start_handle: i32,
        end_handle: i32,
        auth_req: i32,
    ) {
        dbus_generated!()
    }

    #[dbus_method("WriteCharacteristic")]
    fn write_characteristic(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        write_type: GattWriteType,
        auth_req: i32,
        value: Vec<u8>,
    ) -> GattWriteRequestStatus {
        dbus_generated!()
    }

    #[dbus_method("ReadDescriptor")]
    fn read_descriptor(&self, client_id: i32, addr: String, handle: i32, auth_req: i32) {
        dbus_generated!()
    }

    #[dbus_method("WriteDescriptor")]
    fn write_descriptor(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        auth_req: i32,
        value: Vec<u8>,
    ) {
        dbus_generated!()
    }

    #[dbus_method("RegisterForNotification")]
    fn register_for_notification(&self, client_id: i32, addr: String, handle: i32, enable: bool) {
        dbus_generated!()
    }

    #[dbus_method("BeginReliableWrite")]
    fn begin_reliable_write(&mut self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("EndReliableWrite")]
    fn end_reliable_write(&mut self, client_id: i32, addr: String, execute: bool) {
        dbus_generated!()
    }

    #[dbus_method("ReadRemoteRssi")]
    fn read_remote_rssi(&self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("ConfigureMtu")]
    fn configure_mtu(&self, client_id: i32, addr: String, mtu: i32) {
        dbus_generated!()
    }

    #[dbus_method("ConnectionParameterUpdate")]
    fn connection_parameter_update(
        &self,
        client_id: i32,
        addr: String,
        min_interval: i32,
        max_interval: i32,
        latency: i32,
        timeout: i32,
        min_ce_len: u16,
        max_ce_len: u16,
    ) {
        dbus_generated!()
    }

    // GATT Server

    #[dbus_method("RegisterServer")]
    fn register_server(
        &mut self,
        app_uuid: String,
        callback: Box<dyn IBluetoothGattServerCallback + Send>,
        eatt_support: bool,
    ) {
        dbus_generated!()
    }

    #[dbus_method("UnregisterServer")]
    fn unregister_server(&mut self, server_id: i32) {
        dbus_generated!()
    }

    #[dbus_method("ServerConnect")]
    fn server_connect(
        &self,
        server_id: i32,
        addr: String,
        is_direct: bool,
        transport: BtTransport,
    ) -> bool {
        dbus_generated!()
    }

    #[dbus_method("ServerDisconnect")]
    fn server_disconnect(&self, server_id: i32, addr: String) -> bool {
        dbus_generated!()
    }

    #[dbus_method("AddService")]
    fn add_service(&self, server_id: i32, service: BluetoothGattService) {
        dbus_generated!()
    }

    #[dbus_method("RemoveService")]
    fn remove_service(&self, server_id: i32, handle: i32) {
        dbus_generated!()
    }

    #[dbus_method("ClearServices")]
    fn clear_services(&self, server_id: i32) {
        dbus_generated!()
    }

    #[dbus_method("SendResponse")]
    fn send_response(
        &self,
        server_id: i32,
        addr: String,
        request_id: i32,
        status: GattStatus,
        offset: i32,
        value: Vec<u8>,
    ) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SendNotification")]
    fn send_notification(
        &self,
        server_id: i32,
        addr: String,
        handle: i32,
        confirm: bool,
        value: Vec<u8>,
    ) -> bool {
        dbus_generated!()
    }

    #[dbus_method("ServerSetPreferredPhy")]
    fn server_set_preferred_phy(
        &self,
        server_id: i32,
        addr: String,
        tx_phy: LePhy,
        rx_phy: LePhy,
        phy_options: i32,
    ) {
        dbus_generated!()
    }

    #[dbus_method("ServerReadPhy")]
    fn server_read_phy(&self, server_id: i32, addr: String) {
        dbus_generated!()
    }
}

struct IBluetoothGattCallbackDBus {}

impl RPCProxy for IBluetoothGattCallbackDBus {}

#[generate_dbus_exporter(
    export_bluetooth_gatt_callback_dbus_intf,
    "org.chromium.bluetooth.BluetoothGattCallback"
)]
impl IBluetoothGattCallback for IBluetoothGattCallbackDBus {
    #[dbus_method("OnClientRegistered", DBusLog::Disable)]
    fn on_client_registered(&mut self, status: GattStatus, client_id: i32) {}

    #[dbus_method("OnClientConnectionState", DBusLog::Disable)]
    fn on_client_connection_state(
        &mut self,
        status: GattStatus,
        client_id: i32,
        connected: bool,
        addr: String,
    ) {
    }

    #[dbus_method("OnPhyUpdate", DBusLog::Disable)]
    fn on_phy_update(&mut self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus) {}

    #[dbus_method("OnPhyRead", DBusLog::Disable)]
    fn on_phy_read(&mut self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus) {}

    #[dbus_method("OnSearchComplete", DBusLog::Disable)]
    fn on_search_complete(
        &mut self,
        addr: String,
        services: Vec<BluetoothGattService>,
        status: GattStatus,
    ) {
    }

    #[dbus_method("OnCharacteristicRead", DBusLog::Disable)]
    fn on_characteristic_read(
        &mut self,
        addr: String,
        status: GattStatus,
        handle: i32,
        value: Vec<u8>,
    ) {
    }

    #[dbus_method("OnCharacteristicWrite", DBusLog::Disable)]
    fn on_characteristic_write(&mut self, addr: String, status: GattStatus, handle: i32) {}

    #[dbus_method("OnExecuteWrite", DBusLog::Disable)]
    fn on_execute_write(&mut self, addr: String, status: GattStatus) {}

    #[dbus_method("OnDescriptorRead", DBusLog::Disable)]
    fn on_descriptor_read(
        &mut self,
        addr: String,
        status: GattStatus,
        handle: i32,
        value: Vec<u8>,
    ) {
    }

    #[dbus_method("OnDescriptorWrite", DBusLog::Disable)]
    fn on_descriptor_write(&mut self, addr: String, status: GattStatus, handle: i32) {}

    #[dbus_method("OnNotify", DBusLog::Disable)]
    fn on_notify(&mut self, addr: String, handle: i32, value: Vec<u8>) {}

    #[dbus_method("OnReadRemoteRssi", DBusLog::Disable)]
    fn on_read_remote_rssi(&mut self, addr: String, rssi: i32, status: GattStatus) {}

    #[dbus_method("OnConfigureMtu", DBusLog::Disable)]
    fn on_configure_mtu(&mut self, addr: String, mtu: i32, status: GattStatus) {}

    #[dbus_method("OnConnectionUpdated", DBusLog::Disable)]
    fn on_connection_updated(
        &mut self,
        addr: String,
        interval: i32,
        latency: i32,
        timeout: i32,
        status: GattStatus,
    ) {
    }

    #[dbus_method("OnServiceChanged", DBusLog::Disable)]
    fn on_service_changed(&mut self, addr: String) {}
}

#[generate_dbus_exporter(
    export_gatt_server_callback_dbus_intf,
    "org.chromium.bluetooth.BluetoothGattServerCallback"
)]
impl IBluetoothGattServerCallback for IBluetoothGattCallbackDBus {
    #[dbus_method("OnServerRegistered", DBusLog::Disable)]
    fn on_server_registered(&mut self, status: GattStatus, client_id: i32) {}

    #[dbus_method("OnServerConnectionState", DBusLog::Disable)]
    fn on_server_connection_state(&mut self, server_id: i32, connected: bool, addr: String) {}

    #[dbus_method("OnServiceAdded", DBusLog::Disable)]
    fn on_service_added(&mut self, status: GattStatus, service: BluetoothGattService) {}

    #[dbus_method("OnServiceRemoved", DBusLog::Disable)]
    fn on_service_removed(&mut self, status: GattStatus, handle: i32) {}

    #[dbus_method("OnCharacteristicReadRequest", DBusLog::Disable)]
    fn on_characteristic_read_request(
        &mut self,
        addr: String,
        trans_id: i32,
        offset: i32,
        is_long: bool,
        handle: i32,
    ) {
    }

    #[dbus_method("OnDescriptorReadRequest", DBusLog::Disable)]
    fn on_descriptor_read_request(
        &mut self,
        addr: String,
        trans_id: i32,
        offset: i32,
        is_long: bool,
        handle: i32,
    ) {
    }

    #[dbus_method("OnCharacteristicWriteRequest", DBusLog::Disable)]
    fn on_characteristic_write_request(
        &mut self,
        addr: String,
        trans_id: i32,
        offset: i32,
        len: i32,
        is_prep: bool,
        need_rsp: bool,
        handle: i32,
        value: Vec<u8>,
    ) {
    }

    #[dbus_method("OnDescriptorWriteRequest", DBusLog::Disable)]
    fn on_descriptor_write_request(
        &mut self,
        addr: String,
        trans_id: i32,
        offset: i32,
        len: i32,
        is_prep: bool,
        need_rsp: bool,
        handle: i32,
        value: Vec<u8>,
    ) {
    }

    #[dbus_method("OnExecuteWrite", DBusLog::Disable)]
    fn on_execute_write(&mut self, addr: String, trans_id: i32, exec_write: bool) {}

    #[dbus_method("OnNotificationSent", DBusLog::Disable)]
    fn on_notification_sent(&mut self, addr: String, status: GattStatus) {}

    #[dbus_method("OnMtuChanged", DBusLog::Disable)]
    fn on_mtu_changed(&mut self, addr: String, mtu: i32) {}

    #[dbus_method("OnPhyUpdate", DBusLog::Disable)]
    fn on_phy_update(&mut self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus) {}

    #[dbus_method("OnPhyRead", DBusLog::Disable)]
    fn on_phy_read(&mut self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus) {}

    #[dbus_method("OnConnectionUpdated", DBusLog::Disable)]
    fn on_connection_updated(
        &mut self,
        addr: String,
        interval: i32,
        latency: i32,
        timeout: i32,
        status: GattStatus,
    ) {
    }

    #[dbus_method("OnSubrateChange", DBusLog::Disable)]
    fn on_subrate_change(
        &mut self,
        addr: String,
        subrate_factor: i32,
        latency: i32,
        cont_num: i32,
        timeout: i32,
        status: GattStatus,
    ) {
    }
}

#[dbus_propmap(BluetoothServerSocket)]
pub struct BluetoothServerSocketDBus {
    id: SocketId,
    sock_type: SocketType,
    flags: i32,
    psm: Option<i32>,
    channel: Option<i32>,
    name: Option<String>,
    uuid: Option<Uuid>,
}

#[dbus_propmap(BluetoothSocket)]
pub struct BluetoothSocketDBus {
    id: SocketId,
    remote_device: BluetoothDevice,
    sock_type: SocketType,
    flags: i32,
    fd: Option<std::fs::File>,
    port: i32,
    uuid: Option<Uuid>,
    max_rx_size: i32,
    max_tx_size: i32,
}

#[dbus_propmap(SocketResult)]
pub struct SocketResultDBus {
    status: BtStatus,
    id: u64,
}

pub(crate) struct BluetoothSocketManagerDBusRPC {
    client_proxy: ClientDBusProxy,
}

pub(crate) struct BluetoothSocketManagerDBus {
    client_proxy: ClientDBusProxy,
    pub rpc: BluetoothSocketManagerDBusRPC,
}

impl BluetoothSocketManagerDBus {
    fn make_client_proxy(conn: Arc<SyncConnection>, index: i32) -> ClientDBusProxy {
        ClientDBusProxy::new(
            conn,
            String::from("org.chromium.bluetooth"),
            make_object_path(index, "adapter"),
            String::from("org.chromium.bluetooth.SocketManager"),
        )
    }

    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> Self {
        BluetoothSocketManagerDBus {
            client_proxy: Self::make_client_proxy(conn.clone(), index),
            rpc: BluetoothSocketManagerDBusRPC {
                client_proxy: Self::make_client_proxy(conn.clone(), index),
            },
        }
    }
}

#[generate_dbus_interface_client(BluetoothSocketManagerDBusRPC)]
impl IBluetoothSocketManager for BluetoothSocketManagerDBus {
    #[dbus_method("RegisterCallback")]
    fn register_callback(
        &mut self,
        callback: Box<dyn IBluetoothSocketManagerCallbacks + Send>,
    ) -> CallbackId {
        dbus_generated!()
    }

    #[dbus_method("UnregisterCallback")]
    fn unregister_callback(&mut self, callback: CallbackId) -> bool {
        dbus_generated!()
    }

    #[dbus_method("ListenUsingInsecureL2capChannel")]
    fn listen_using_insecure_l2cap_channel(&mut self, callback: CallbackId) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("ListenUsingInsecureL2capLeChannel")]
    fn listen_using_insecure_l2cap_le_channel(&mut self, callback: CallbackId) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("ListenUsingL2capChannel")]
    fn listen_using_l2cap_channel(&mut self, callback: CallbackId) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("ListenUsingL2capLeChannel")]
    fn listen_using_l2cap_le_channel(&mut self, callback: CallbackId) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("ListenUsingInsecureRfcommWithServiceRecord")]
    fn listen_using_insecure_rfcomm_with_service_record(
        &mut self,
        callback: CallbackId,
        name: String,
        uuid: Uuid,
    ) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("ListenUsingRfcommWithServiceRecord")]
    fn listen_using_rfcomm_with_service_record(
        &mut self,
        callback: CallbackId,
        name: String,
        uuid: Uuid,
    ) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("ListenUsingRfcomm")]
    fn listen_using_rfcomm(
        &mut self,
        callback: CallbackId,
        channel: Option<i32>,
        application_uuid: Option<Uuid>,
        name: Option<String>,
        flags: Option<i32>,
    ) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("CreateInsecureL2capChannel")]
    fn create_insecure_l2cap_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("CreateInsecureL2capLeChannel")]
    fn create_insecure_l2cap_le_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("CreateL2capChannel")]
    fn create_l2cap_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("CreateL2capLeChannel")]
    fn create_l2cap_le_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("CreateInsecureRfcommSocketToServiceRecord")]
    fn create_insecure_rfcomm_socket_to_service_record(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        uuid: Uuid,
    ) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("CreateRfcommSocketToServiceRecord")]
    fn create_rfcomm_socket_to_service_record(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        uuid: Uuid,
    ) -> SocketResult {
        dbus_generated!()
    }

    #[dbus_method("Accept")]
    fn accept(&mut self, callback: CallbackId, id: SocketId, timeout_ms: Option<u32>) -> BtStatus {
        dbus_generated!()
    }

    #[dbus_method("Close")]
    fn close(&mut self, callback: CallbackId, id: SocketId) -> BtStatus {
        dbus_generated!()
    }
}

struct IBluetoothSocketManagerCallbacksDBus {}

impl RPCProxy for IBluetoothSocketManagerCallbacksDBus {}

#[generate_dbus_exporter(
    export_socket_callback_dbus_intf,
    "org.chromium.bluetooth.SocketManagerCallback"
)]
impl IBluetoothSocketManagerCallbacks for IBluetoothSocketManagerCallbacksDBus {
    #[dbus_method("OnIncomingSocketReady", DBusLog::Disable)]
    fn on_incoming_socket_ready(&mut self, socket: BluetoothServerSocket, status: BtStatus) {
        dbus_generated!()
    }

    #[dbus_method("OnIncomingSocketClosed", DBusLog::Disable)]
    fn on_incoming_socket_closed(&mut self, listener_id: SocketId, reason: BtStatus) {
        dbus_generated!()
    }

    #[dbus_method("OnHandleIncomingConnection", DBusLog::Disable)]
    fn on_handle_incoming_connection(
        &mut self,
        listener_id: SocketId,
        connection: BluetoothSocket,
    ) {
        dbus_generated!()
    }

    #[dbus_method("OnOutgoingConnectionResult", DBusLog::Disable)]
    fn on_outgoing_connection_result(
        &mut self,
        connecting_id: SocketId,
        result: BtStatus,
        socket: Option<BluetoothSocket>,
    ) {
        dbus_generated!()
    }
}

pub(crate) struct SuspendDBus {
    client_proxy: ClientDBusProxy,
}

impl SuspendDBus {
    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> SuspendDBus {
        SuspendDBus {
            client_proxy: ClientDBusProxy::new(
                conn.clone(),
                String::from("org.chromium.bluetooth"),
                make_object_path(index, "adapter"),
                String::from("org.chromium.bluetooth.Suspend"),
            ),
        }
    }
}

#[generate_dbus_interface_client]
impl ISuspend for SuspendDBus {
    #[dbus_method("RegisterCallback")]
    fn register_callback(&mut self, _callback: Box<dyn ISuspendCallback + Send>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("UnregisterCallback")]
    fn unregister_callback(&mut self, _callback_id: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Suspend")]
    fn suspend(&mut self, _suspend_type: SuspendType, suspend_id: i32) {
        dbus_generated!()
    }

    #[dbus_method("Resume")]
    fn resume(&mut self) -> bool {
        dbus_generated!()
    }
}

struct ISuspendCallbackDBus {}

impl RPCProxy for ISuspendCallbackDBus {}

#[generate_dbus_exporter(
    export_suspend_callback_dbus_intf,
    "org.chromium.bluetooth.SuspendCallback"
)]
impl ISuspendCallback for ISuspendCallbackDBus {
    #[dbus_method("OnCallbackRegistered", DBusLog::Disable)]
    fn on_callback_registered(&mut self, callback_id: u32) {}
    #[dbus_method("OnSuspendReady", DBusLog::Disable)]
    fn on_suspend_ready(&mut self, suspend_id: i32) {}
    #[dbus_method("OnResumed", DBusLog::Disable)]
    fn on_resumed(&mut self, suspend_id: i32) {}
}

pub(crate) struct BluetoothTelephonyDBusRPC {
    client_proxy: ClientDBusProxy,
}

pub(crate) struct BluetoothTelephonyDBus {
    client_proxy: ClientDBusProxy,
    pub rpc: BluetoothTelephonyDBusRPC,
}

impl BluetoothTelephonyDBus {
    fn make_client_proxy(conn: Arc<SyncConnection>, index: i32) -> ClientDBusProxy {
        ClientDBusProxy::new(
            conn.clone(),
            String::from("org.chromium.bluetooth"),
            make_object_path(index, "telephony"),
            String::from("org.chromium.bluetooth.BluetoothTelephony"),
        )
    }

    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> BluetoothTelephonyDBus {
        BluetoothTelephonyDBus {
            client_proxy: Self::make_client_proxy(conn.clone(), index),
            rpc: BluetoothTelephonyDBusRPC {
                client_proxy: Self::make_client_proxy(conn.clone(), index),
            },
        }
    }
}

#[generate_dbus_interface_client(BluetoothTelephonyDBusRPC)]
impl IBluetoothTelephony for BluetoothTelephonyDBus {
    #[dbus_method("RegisterTelephonyCallback")]
    fn register_telephony_callback(
        &mut self,
        callback: Box<dyn IBluetoothTelephonyCallback + Send>,
    ) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetNetworkAvailable")]
    fn set_network_available(&mut self, network_available: bool) {
        dbus_generated!()
    }
    #[dbus_method("SetRoaming")]
    fn set_roaming(&mut self, roaming: bool) {
        dbus_generated!()
    }
    #[dbus_method("SetSignalStrength")]
    fn set_signal_strength(&mut self, signal_strength: i32) -> bool {
        dbus_generated!()
    }
    #[dbus_method("SetBatteryLevel")]
    fn set_battery_level(&mut self, battery_level: i32) -> bool {
        dbus_generated!()
    }
    #[dbus_method("SetPhoneOpsEnabled")]
    fn set_phone_ops_enabled(&mut self, enable: bool) {
        dbus_generated!()
    }
    #[dbus_method("SetMpsQualificationEnabled")]
    fn set_mps_qualification_enabled(&mut self, enable: bool) {
        dbus_generated!()
    }
    #[dbus_method("IncomingCall")]
    fn incoming_call(&mut self, number: String) -> bool {
        dbus_generated!()
    }
    #[dbus_method("DialingCall")]
    fn dialing_call(&mut self, number: String) -> bool {
        dbus_generated!()
    }
    #[dbus_method("AnswerCall")]
    fn answer_call(&mut self) -> bool {
        dbus_generated!()
    }
    #[dbus_method("HangupCall")]
    fn hangup_call(&mut self) -> bool {
        dbus_generated!()
    }
    #[dbus_method("SetMemoryCall")]
    fn set_memory_call(&mut self, number: Option<String>) -> bool {
        dbus_generated!()
    }
    #[dbus_method("SetLastCall")]
    fn set_last_call(&mut self, number: Option<String>) -> bool {
        dbus_generated!()
    }
    #[dbus_method("ReleaseHeld")]
    fn release_held(&mut self) -> bool {
        dbus_generated!()
    }
    #[dbus_method("ReleaseActiveAcceptHeld")]
    fn release_active_accept_held(&mut self) -> bool {
        dbus_generated!()
    }
    #[dbus_method("HoldActiveAcceptHeld")]
    fn hold_active_accept_held(&mut self) -> bool {
        dbus_generated!()
    }
    #[dbus_method("AudioConnect")]
    fn audio_connect(&mut self, address: String) -> bool {
        dbus_generated!()
    }
    #[dbus_method("AudioDisconnect")]
    fn audio_disconnect(&mut self, address: String) {
        dbus_generated!()
    }
}

struct IBluetoothTelephonyCallbackDBus {}

impl RPCProxy for IBluetoothTelephonyCallbackDBus {}

#[generate_dbus_exporter(
    export_bluetooth_telephony_callback_dbus_intf,
    "org.chromium.bluetooth.BluetoothTelephonyCallback"
)]
impl IBluetoothTelephonyCallback for IBluetoothTelephonyCallbackDBus {
    #[dbus_method("OnTelephonyUse")]
    fn on_telephony_use(&mut self, addr: String, state: bool) {
        dbus_generated!()
    }
}

pub(crate) struct BluetoothQADBusRPC {
    client_proxy: ClientDBusProxy,
}

pub(crate) struct BluetoothQADBus {
    client_proxy: ClientDBusProxy,
    pub rpc: BluetoothQADBusRPC,
}

impl BluetoothQADBus {
    fn make_client_proxy(conn: Arc<SyncConnection>, index: i32) -> ClientDBusProxy {
        ClientDBusProxy::new(
            conn.clone(),
            String::from("org.chromium.bluetooth"),
            make_object_path(index, "qa"),
            String::from("org.chromium.bluetooth.BluetoothQA"),
        )
    }

    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> BluetoothQADBus {
        BluetoothQADBus {
            client_proxy: Self::make_client_proxy(conn.clone(), index),
            rpc: BluetoothQADBusRPC { client_proxy: Self::make_client_proxy(conn.clone(), index) },
        }
    }
}

#[generate_dbus_interface_client(BluetoothQADBusRPC)]
impl IBluetoothQA for BluetoothQADBus {
    #[dbus_method("RegisterQACallback")]
    fn register_qa_callback(&mut self, callback: Box<dyn IBluetoothQACallback + Send>) -> u32 {
        dbus_generated!()
    }
    #[dbus_method("UnregisterQACallback")]
    fn unregister_qa_callback(&mut self, callback_id: u32) -> bool {
        dbus_generated!()
    }
    #[dbus_method("AddMediaPlayer")]
    fn add_media_player(&self, name: String, browsing_supported: bool) {
        dbus_generated!()
    }
    #[dbus_method("RfcommSendMsc")]
    fn rfcomm_send_msc(&self, dlci: u8, addr: String) {
        dbus_generated!()
    }
    #[dbus_method("FetchDiscoverableMode")]
    fn fetch_discoverable_mode(&self) {
        dbus_generated!()
    }
    #[dbus_method("FetchConnectable")]
    fn fetch_connectable(&self) {
        dbus_generated!()
    }
    #[dbus_method("SetConnectable")]
    fn set_connectable(&self, mode: bool) {
        dbus_generated!()
    }
    #[dbus_method("FetchAlias")]
    fn fetch_alias(&self) {
        dbus_generated!()
    }
    #[dbus_method("GetModalias")]
    fn get_modalias(&self) -> String {
        dbus_generated!()
    }
    #[dbus_method("GetHIDReport")]
    fn get_hid_report(&self, addr: String, report_type: BthhReportType, report_id: u8) {
        dbus_generated!()
    }
    #[dbus_method("SetHIDReport")]
    fn set_hid_report(&self, addr: String, report_type: BthhReportType, report: String) {
        dbus_generated!()
    }
    #[dbus_method("SendHIDData")]
    fn send_hid_data(&self, addr: String, data: String) {
        dbus_generated!()
    }
}

struct IBluetoothQACallbackDBus {}

impl RPCProxy for IBluetoothQACallbackDBus {}

#[generate_dbus_exporter(
    export_qa_callback_dbus_intf,
    "org.chromium.bluetooth.QACallback",
    DBusLog::Disable
)]
impl IBluetoothQACallback for IBluetoothQACallbackDBus {
    #[dbus_method("OnFetchDiscoverableModeComplete", DBusLog::Disable)]
    fn on_fetch_discoverable_mode_completed(&mut self, disc_mode: BtDiscMode) {
        dbus_generated!()
    }
    #[dbus_method("OnFetchConnectableComplete", DBusLog::Disable)]
    fn on_fetch_connectable_completed(&mut self, connectable: bool) {
        dbus_generated!()
    }
    #[dbus_method("OnSetConnectableComplete", DBusLog::Disable)]
    fn on_set_connectable_completed(&mut self, succeed: bool) {
        dbus_generated!()
    }
    #[dbus_method("OnFetchAliasComplete", DBusLog::Disable)]
    fn on_fetch_alias_completed(&mut self, alias: String) {
        dbus_generated!()
    }
    #[dbus_method("OnGetHIDReportComplete", DBusLog::Disable)]
    fn on_get_hid_report_completed(&mut self, status: BtStatus) {
        dbus_generated!()
    }
    #[dbus_method("OnSetHIDReportComplete", DBusLog::Disable)]
    fn on_set_hid_report_completed(&mut self, status: BtStatus) {
        dbus_generated!()
    }
    #[dbus_method("OnSendHIDDataComplete", DBusLog::Disable)]
    fn on_send_hid_data_completed(&mut self, status: BtStatus) {
        dbus_generated!()
    }
}

pub(crate) struct BluetoothMediaDBusRPC {
    client_proxy: ClientDBusProxy,
}

pub(crate) struct BluetoothMediaDBus {
    client_proxy: ClientDBusProxy,
    pub rpc: BluetoothMediaDBusRPC,
}

impl BluetoothMediaDBus {
    fn make_client_proxy(conn: Arc<SyncConnection>, index: i32) -> ClientDBusProxy {
        ClientDBusProxy::new(
            conn.clone(),
            String::from("org.chromium.bluetooth"),
            make_object_path(index, "media"),
            String::from("org.chromium.bluetooth.BluetoothMedia"),
        )
    }

    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> BluetoothMediaDBus {
        BluetoothMediaDBus {
            client_proxy: Self::make_client_proxy(conn.clone(), index),
            rpc: BluetoothMediaDBusRPC {
                client_proxy: Self::make_client_proxy(conn.clone(), index),
            },
        }
    }
}

#[generate_dbus_interface_client(BluetoothMediaDBusRPC)]
impl IBluetoothMedia for BluetoothMediaDBus {
    #[dbus_method("RegisterCallback")]
    fn register_callback(&mut self, callback: Box<dyn IBluetoothMediaCallback + Send>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Initialize")]
    fn initialize(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Cleanup")]
    fn cleanup(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Connect")]
    fn connect(&mut self, address: String) {
        dbus_generated!()
    }

    #[dbus_method("Disconnect")]
    fn disconnect(&mut self, address: String) {
        dbus_generated!()
    }

    #[dbus_method("SetActiveDevice")]
    fn set_active_device(&mut self, address: String) {
        dbus_generated!()
    }

    #[dbus_method("ResetActiveDevice")]
    fn reset_active_device(&mut self) {
        dbus_generated!()
    }

    #[dbus_method("SetHfpActiveDevice")]
    fn set_hfp_active_device(&mut self, address: String) {
        dbus_generated!()
    }

    #[dbus_method("SetAudioConfig")]
    fn set_audio_config(
        &mut self,
        address: String,
        codec_type: A2dpCodecIndex,
        sample_rate: A2dpCodecSampleRate,
        bits_per_sample: A2dpCodecBitsPerSample,
        channel_mode: A2dpCodecChannelMode,
    ) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetVolume")]
    fn set_volume(&mut self, volume: u8) {
        dbus_generated!()
    }

    #[dbus_method("SetHfpVolume")]
    fn set_hfp_volume(&mut self, volume: u8, address: String) {
        dbus_generated!()
    }

    #[dbus_method("StartAudioRequest")]
    fn start_audio_request(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetA2dpAudioStarted")]
    fn get_a2dp_audio_started(&mut self, address: String) -> bool {
        dbus_generated!()
    }

    #[dbus_method("StopAudioRequest")]
    fn stop_audio_request(&mut self) {
        dbus_generated!()
    }

    #[dbus_method("StartScoCall")]
    fn start_sco_call(
        &mut self,
        address: String,
        sco_offload: bool,
        disabled_codecs: HfpCodecBitId,
    ) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetHfpAudioFinalCodecs")]
    fn get_hfp_audio_final_codecs(&mut self, address: String) -> u8 {
        dbus_generated!()
    }

    #[dbus_method("StopScoCall")]
    fn stop_sco_call(&mut self, address: String) {
        dbus_generated!()
    }

    #[dbus_method("GetPresentationPosition")]
    fn get_presentation_position(&mut self) -> PresentationPosition {
        dbus_generated!()
    }

    // Temporary AVRCP-related meida DBUS APIs. The following APIs intercept between Chrome CRAS
    // and cras_server as an expedited solution for AVRCP implementation. The APIs are subject to
    // change when retiring Chrome CRAS.
    #[dbus_method("SetPlayerPlaybackStatus")]
    fn set_player_playback_status(&mut self, status: String) {
        dbus_generated!()
    }

    #[dbus_method("SetPlayerPosition")]
    fn set_player_position(&mut self, position_us: i64) {
        dbus_generated!()
    }

    #[dbus_method("SetPlayerMetadata")]
    fn set_player_metadata(&mut self, metadata: PlayerMetadata) {
        dbus_generated!()
    }

    #[dbus_method("TriggerDebugDump")]
    fn trigger_debug_dump(&mut self) {
        dbus_generated!()
    }
}

struct IBluetoothMediaCallbackDBus {}

impl RPCProxy for IBluetoothMediaCallbackDBus {}

#[generate_dbus_exporter(
    export_bluetooth_media_callback_dbus_intf,
    "org.chromium.bluetooth.BluetoothMediaCallback"
)]
impl IBluetoothMediaCallback for IBluetoothMediaCallbackDBus {
    #[dbus_method("OnBluetoothAudioDeviceAdded", DBusLog::Disable)]
    fn on_bluetooth_audio_device_added(&mut self, device: BluetoothAudioDevice) {}

    #[dbus_method("OnBluetoothAudioDeviceRemoved", DBusLog::Disable)]
    fn on_bluetooth_audio_device_removed(&mut self, addr: String) {}

    #[dbus_method("OnAbsoluteVolumeSupportedChanged", DBusLog::Disable)]
    fn on_absolute_volume_supported_changed(&mut self, supported: bool) {}

    #[dbus_method("OnAbsoluteVolumeChanged", DBusLog::Disable)]
    fn on_absolute_volume_changed(&mut self, volume: u8) {}

    #[dbus_method("OnHfpVolumeChanged", DBusLog::Disable)]
    fn on_hfp_volume_changed(&mut self, volume: u8, addr: String) {}

    #[dbus_method("OnHfpAudioDisconnected", DBusLog::Disable)]
    fn on_hfp_audio_disconnected(&mut self, addr: String) {}

    #[dbus_method("OnHfpDebugDump", DBusLog::Disable)]
    fn on_hfp_debug_dump(
        &mut self,
        active: bool,
        codec_id: u16,
        total_num_decoded_frames: i32,
        pkt_loss_ratio: f64,
        begin_ts: u64,
        end_ts: u64,
        pkt_status_in_hex: String,
        pkt_status_in_binary: String,
    ) {
    }
}

pub(crate) struct BatteryManagerDBusRPC {
    client_proxy: ClientDBusProxy,
}

pub(crate) struct BatteryManagerDBus {
    client_proxy: ClientDBusProxy,
    pub rpc: BatteryManagerDBusRPC,
}

impl BatteryManagerDBus {
    fn make_client_proxy(conn: Arc<SyncConnection>, index: i32) -> ClientDBusProxy {
        ClientDBusProxy::new(
            conn.clone(),
            String::from("org.chromium.bluetooth"),
            make_object_path(index, "battery_manager"),
            String::from("org.chromium.bluetooth.BatteryManager"),
        )
    }

    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> BatteryManagerDBus {
        BatteryManagerDBus {
            client_proxy: Self::make_client_proxy(conn.clone(), index),
            rpc: BatteryManagerDBusRPC {
                client_proxy: Self::make_client_proxy(conn.clone(), index),
            },
        }
    }
}

#[generate_dbus_interface_client(BatteryManagerDBusRPC)]
impl IBatteryManager for BatteryManagerDBus {
    #[dbus_method("RegisterBatteryCallback")]
    fn register_battery_callback(
        &mut self,
        battery_manager_callback: Box<dyn IBatteryManagerCallback + Send>,
    ) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("UnregisterBatteryCallback")]
    fn unregister_battery_callback(&mut self, callback_id: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetBatteryInformation")]
    fn get_battery_information(&self, remote_address: String) -> Option<BatterySet> {
        dbus_generated!()
    }
}

#[dbus_propmap(BatterySet)]
pub struct BatterySetDBus {
    address: String,
    source_uuid: String,
    source_info: String,
    batteries: Vec<Battery>,
}

#[dbus_propmap(Battery)]
pub struct BatteryDBus {
    percentage: u32,
    variant: String,
}

struct IBatteryManagerCallbackDBus {}

impl RPCProxy for IBatteryManagerCallbackDBus {}

#[generate_dbus_exporter(
    export_battery_manager_callback_dbus_intf,
    "org.chromium.bluetooth.BatteryManagerCallback"
)]
impl IBatteryManagerCallback for IBatteryManagerCallbackDBus {
    #[dbus_method("OnBatteryInfoUpdated")]
    fn on_battery_info_updated(&mut self, remote_address: String, battery_set: BatterySet) {}
}
