//! Shim for `bt_interface_t`, providing access to libbluetooth.
//!
//! This is a shim interface for calling the C++ bluetooth interface via Rust.

use crate::bindings::root as bindings;
use crate::topstack::get_dispatchers;
use crate::utils::{LTCheckedPtr, LTCheckedPtrMut};
use num_derive::{FromPrimitive, ToPrimitive};
use num_traits::cast::{FromPrimitive, ToPrimitive};
use std::cmp;
use std::convert::TryFrom;
use std::fmt::{Debug, Display, Formatter, Result};
use std::hash::{Hash, Hasher};
use std::mem;
use std::os::raw::c_char;
use std::sync::{Arc, Mutex};
use std::vec::Vec;
use topshim_macros::cb_variant;

use cxx::{type_id, ExternType};

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtState {
    Off = 0,
    On,
}

impl From<bindings::bt_state_t> for BtState {
    fn from(item: bindings::bt_state_t) -> Self {
        BtState::from_u32(item).unwrap_or(BtState::Off)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd, Copy)]
#[repr(u32)]
pub enum BtTransport {
    Auto = 0,
    Bredr,
    Le,
}

impl From<i32> for BtTransport {
    fn from(item: i32) -> Self {
        BtTransport::from_i32(item).unwrap_or(BtTransport::Auto)
    }
}

impl From<BtTransport> for i32 {
    fn from(item: BtTransport) -> Self {
        item.to_i32().unwrap_or(0)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtSspVariant {
    PasskeyConfirmation = 0,
    PasskeyEntry,
    Consent,
    PasskeyNotification,
}

impl From<bindings::bt_ssp_variant_t> for BtSspVariant {
    fn from(item: bindings::bt_ssp_variant_t) -> Self {
        BtSspVariant::from_u32(item).unwrap_or(BtSspVariant::PasskeyConfirmation)
    }
}

impl From<BtSspVariant> for bindings::bt_ssp_variant_t {
    fn from(item: BtSspVariant) -> Self {
        item.to_u32().unwrap_or(0)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtBondState {
    NotBonded = 0,
    Bonding,
    Bonded,
}

impl From<bindings::bt_bond_state_t> for BtBondState {
    fn from(item: bindings::bt_bond_state_t) -> Self {
        BtBondState::from_u32(item).unwrap_or(BtBondState::NotBonded)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtConnectionState {
    NotConnected = 0,
    ConnectedOnly = 1,
    EncryptedBredr = 3,
    EncryptedLe = 5,
    EncryptedBoth = 7,
}

impl From<i32> for BtConnectionState {
    fn from(item: i32) -> Self {
        let fallback = if item > 0 {
            BtConnectionState::ConnectedOnly
        } else {
            BtConnectionState::NotConnected
        };

        BtConnectionState::from_i32(item).unwrap_or(fallback)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtAclState {
    Connected = 0,
    Disconnected,
}

impl From<bindings::bt_acl_state_t> for BtAclState {
    fn from(item: bindings::bt_acl_state_t) -> Self {
        BtAclState::from_u32(item).unwrap_or(BtAclState::Disconnected)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtDeviceType {
    Unknown = 0,
    Bredr,
    Ble,
    Dual,
}

#[derive(Clone, Debug, Eq, Hash, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtPropertyType {
    BdName = 0x1,
    BdAddr,
    Uuids,
    ClassOfDevice,
    TypeOfDevice,
    ServiceRecord,
    AdapterScanMode,
    AdapterBondedDevices,
    AdapterDiscoverableTimeout,
    RemoteFriendlyName,
    RemoteRssi,
    RemoteVersionInfo,
    LocalLeFeatures,
    LocalIoCaps,
    LocalIoCapsBle,
    DynamicAudioBuffer,
    RemoteIsCoordinatedSetMember,
    Appearance,
    VendorProductInfo,
    // Unimplemented:
    //  BT_PROPERTY_WL_MEDIA_PLAYERS_LIST,
    //  BT_PROPERTY_REMOTE_ASHA_CAPABILITY,
    //  BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID,
    //  BT_PROPERTY_REMOTE_MODEL_NUM,
    RemoteAddrType = 0x18,

    Unknown = 0xFE,
    RemoteDeviceTimestamp = 0xFF,
}

impl From<u32> for BtPropertyType {
    fn from(item: u32) -> Self {
        BtPropertyType::from_u32(item).unwrap_or(BtPropertyType::Unknown)
    }
}

impl From<BtPropertyType> for u32 {
    fn from(item: BtPropertyType) -> Self {
        item.to_u32().unwrap_or(0)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtDiscoveryState {
    Stopped = 0x0,
    Started,
}

impl From<u32> for BtDiscoveryState {
    fn from(item: u32) -> Self {
        BtDiscoveryState::from_u32(item).unwrap_or(BtDiscoveryState::Stopped)
    }
}

#[derive(Clone, Copy, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtStatus {
    Success = 0,
    Fail,
    NotReady,
    NoMemory,
    Busy,
    Done,
    Unsupported,
    InvalidParam,
    Unhandled,
    AuthFailure,
    RemoteDeviceDown,
    AuthRejected,
    JniEnvironmentError,
    JniThreadAttachError,
    WakeLockError,
    Timeout,

    // Any statuses that couldn't be cleanly converted
    Unknown = 0xff,
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtConnectionDirection {
    Unknown = 0,
    Outgoing,
    Incoming,
}

impl From<u32> for BtConnectionDirection {
    fn from(item: u32) -> Self {
        BtConnectionDirection::from_u32(item).unwrap_or(BtConnectionDirection::Unknown)
    }
}

pub fn ascii_to_string(data: &[u8], length: usize) -> String {
    // We need to reslice data because from_utf8 tries to interpret the
    // whole slice and not just what is before the null terminated portion
    let ascii = data
        .iter()
        .enumerate()
        .take_while(|&(pos, &c)| c != 0 && pos < length)
        .map(|(_pos, &x)| x.clone())
        .collect::<Vec<u8>>();

    return String::from_utf8(ascii).unwrap_or_default();
}

fn u32_from_bytes(item: &[u8]) -> u32 {
    let mut u: [u8; 4] = [0; 4];
    let len = std::cmp::min(item.len(), 4);
    u[0..len].copy_from_slice(&item);
    u32::from_ne_bytes(u)
}

fn u16_from_bytes(item: &[u8]) -> u16 {
    let mut u: [u8; 2] = [0; 2];
    let len = std::cmp::min(item.len(), 2);
    u[0..len].copy_from_slice(&item);
    u16::from_ne_bytes(u)
}

impl From<bindings::bt_status_t> for BtStatus {
    fn from(item: bindings::bt_status_t) -> Self {
        match BtStatus::from_u32(item) {
            Some(x) => x,
            _ => BtStatus::Unknown,
        }
    }
}

impl Into<u32> for BtStatus {
    fn into(self) -> u32 {
        self.to_u32().unwrap_or_default()
    }
}

impl Into<i32> for BtStatus {
    fn into(self) -> i32 {
        self.to_i32().unwrap_or_default()
    }
}

impl From<bindings::bt_bdname_t> for String {
    fn from(item: bindings::bt_bdname_t) -> Self {
        ascii_to_string(&item.name, item.name.len())
    }
}

#[derive(Debug, Clone)]
pub struct BtServiceRecord {
    pub uuid: bindings::bluetooth::Uuid,
    pub channel: u16,
    pub name: String,
}

impl From<bindings::bt_service_record_t> for BtServiceRecord {
    fn from(item: bindings::bt_service_record_t) -> Self {
        let name = item.name.iter().map(|&x| x.clone() as u8).collect::<Vec<u8>>();

        BtServiceRecord {
            uuid: item.uuid,
            channel: item.channel,
            name: ascii_to_string(name.as_slice(), name.len()),
        }
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtScanMode {
    None_,
    Connectable,
    ConnectableDiscoverable,
    ConnectableLimitedDiscoverable,
}

impl From<bindings::bt_scan_mode_t> for BtScanMode {
    fn from(item: bindings::bt_scan_mode_t) -> Self {
        BtScanMode::from_u32(item).unwrap_or(BtScanMode::None_)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtDiscMode {
    // reference to system/stack/btm/neighbor_inquiry.h
    NonDiscoverable = 0,
    LimitedDiscoverable = 1,
    GeneralDiscoverable = 2,
}

impl From<u32> for BtDiscMode {
    fn from(num: u32) -> Self {
        BtDiscMode::from_u32(num).unwrap_or(BtDiscMode::NonDiscoverable)
    }
}

impl Into<u32> for BtDiscMode {
    fn into(self) -> u32 {
        self.to_u32().unwrap_or(0)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtThreadEvent {
    Associate = 0,
    Disassociate,
}

impl From<bindings::bt_cb_thread_evt> for BtThreadEvent {
    fn from(item: bindings::bt_cb_thread_evt) -> Self {
        BtThreadEvent::from_u32(item).unwrap_or(BtThreadEvent::Associate)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtIoCap {
    Out,
    InOut,
    In,
    None_,
    KbDisp,
    Max,
    Unknown = 0xff,
}

impl From<bindings::bt_io_cap_t> for BtIoCap {
    fn from(item: bindings::bt_io_cap_t) -> Self {
        BtIoCap::from_u32(item).unwrap_or(BtIoCap::Unknown)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtAddrType {
    Public,
    Random,
    PublicId,
    RandomId,
    Unknown = 0xfe,
    Anonymous = 0xff,
}

impl From<u32> for BtAddrType {
    fn from(num: u32) -> Self {
        BtAddrType::from_u32(num).unwrap_or(BtAddrType::Unknown)
    }
}

impl Into<u32> for BtAddrType {
    fn into(self) -> u32 {
        self.to_u32().unwrap_or(0)
    }
}

pub type BtHciErrorCode = u8;
pub type BtLocalLeFeatures = bindings::bt_local_le_features_t;
pub type BtPinCode = bindings::bt_pin_code_t;
pub type BtRemoteVersion = bindings::bt_remote_version_t;
pub type BtVendorProductInfo = bindings::bt_vendor_product_info_t;
pub type Uuid = bindings::bluetooth::Uuid;
pub type Uuid128Bit = bindings::bluetooth::Uuid_UUID128Bit;

impl TryFrom<Uuid> for Vec<u8> {
    type Error = &'static str;

    fn try_from(value: Uuid) -> std::result::Result<Self, Self::Error> {
        Ok((&value.uu).to_vec())
    }
}

impl TryFrom<Vec<u8>> for Uuid {
    type Error = &'static str;

    fn try_from(value: Vec<u8>) -> std::result::Result<Self, Self::Error> {
        // base UUID defined in the Bluetooth specification
        let mut uu: [u8; 16] =
            [0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x10, 0x0, 0x80, 0x0, 0x0, 0x80, 0x5f, 0x9b, 0x34, 0xfb];
        match value.len() {
            2 => {
                uu[2..4].copy_from_slice(&value[0..2]);
                Ok(Uuid::from(uu))
            }
            4 => {
                uu[0..4].copy_from_slice(&value[0..4]);
                Ok(Uuid::from(uu))
            }
            16 => {
                uu.copy_from_slice(&value[0..16]);
                Ok(Uuid::from(uu))
            }
            _ => {
                Err("Vector size must be exactly 2 (16 bit UUID), 4 (32 bit UUID), or 16 (128 bit UUID).")
            }
        }
    }
}

impl From<[u8; 16]> for Uuid {
    fn from(value: [u8; 16]) -> Self {
        Self { uu: value }
    }
}

impl From<Uuid> for [u8; 16] {
    fn from(uuid: Uuid) -> Self {
        uuid.uu
    }
}

impl Hash for Uuid {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.uu.hash(state);
    }
}

impl Uuid {
    /// Creates a Uuid from little endian slice of bytes
    pub fn try_from_little_endian(value: &[u8]) -> std::result::Result<Uuid, &'static str> {
        Uuid::try_from(value.iter().rev().cloned().collect::<Vec<u8>>())
    }

    /// Formats this UUID to a human-readable representation.
    pub fn format(uuid: &Uuid128Bit, f: &mut Formatter) -> Result {
        write!(f, "{:02x}{:02x}{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}{:02x}{:02x}{:02x}{:02x}",
            uuid[0], uuid[1], uuid[2], uuid[3],
            uuid[4], uuid[5],
            uuid[6], uuid[7],
            uuid[8], uuid[9],
            uuid[10], uuid[11], uuid[12], uuid[13], uuid[14], uuid[15])
    }

    pub fn empty() -> Uuid {
        unsafe { bindings::bluetooth::Uuid_kEmpty }
    }
}

impl Display for Uuid {
    fn fmt(&self, f: &mut Formatter) -> Result {
        Uuid::format(&self.uu, f)
    }
}

/// All supported Bluetooth properties after conversion.
#[derive(Debug, Clone)]
pub enum BluetoothProperty {
    BdName(String),
    BdAddr(RawAddress),
    Uuids(Vec<Uuid>),
    ClassOfDevice(u32),
    TypeOfDevice(BtDeviceType),
    ServiceRecord(BtServiceRecord),
    AdapterScanMode(BtScanMode),
    AdapterBondedDevices(Vec<RawAddress>),
    AdapterDiscoverableTimeout(u32),
    RemoteFriendlyName(String),
    RemoteRssi(i8),
    RemoteVersionInfo(BtRemoteVersion),
    LocalLeFeatures(BtLocalLeFeatures),
    LocalIoCaps(BtIoCap),
    LocalIoCapsBle(BtIoCap),
    DynamicAudioBuffer(),
    RemoteIsCoordinatedSetMember(bool),
    Appearance(u16),
    VendorProductInfo(BtVendorProductInfo),
    RemoteAddrType(BtAddrType),
    RemoteDeviceTimestamp(),

    Unknown(),
}

/// Unknown or invalid RSSI value.
/// Per Core v5.3, Vol 4, E, 7.5.4. Valid RSSI is represent in 1-byte with the range:
/// BR/EDR: -128 to 127
/// LE: -127 to 20, 127
/// Set 127 as invalid value also aligns with bluez.
pub const INVALID_RSSI: i8 = 127;

/// Wherever names are sent in bindings::bt_property_t, the size of the character
/// arrays are 256. Keep one extra byte for null termination.
const PROPERTY_NAME_MAX: usize = 255;

impl BluetoothProperty {
    pub fn get_type(&self) -> BtPropertyType {
        match &*self {
            BluetoothProperty::BdName(_) => BtPropertyType::BdName,
            BluetoothProperty::BdAddr(_) => BtPropertyType::BdAddr,
            BluetoothProperty::Uuids(_) => BtPropertyType::Uuids,
            BluetoothProperty::ClassOfDevice(_) => BtPropertyType::ClassOfDevice,
            BluetoothProperty::TypeOfDevice(_) => BtPropertyType::TypeOfDevice,
            BluetoothProperty::ServiceRecord(_) => BtPropertyType::ServiceRecord,
            BluetoothProperty::AdapterScanMode(_) => BtPropertyType::AdapterScanMode,
            BluetoothProperty::AdapterBondedDevices(_) => BtPropertyType::AdapterBondedDevices,
            BluetoothProperty::AdapterDiscoverableTimeout(_) => {
                BtPropertyType::AdapterDiscoverableTimeout
            }
            BluetoothProperty::RemoteFriendlyName(_) => BtPropertyType::RemoteFriendlyName,
            BluetoothProperty::RemoteRssi(_) => BtPropertyType::RemoteRssi,
            BluetoothProperty::RemoteVersionInfo(_) => BtPropertyType::RemoteVersionInfo,
            BluetoothProperty::LocalLeFeatures(_) => BtPropertyType::LocalLeFeatures,
            BluetoothProperty::LocalIoCaps(_) => BtPropertyType::LocalIoCaps,
            BluetoothProperty::LocalIoCapsBle(_) => BtPropertyType::LocalIoCapsBle,
            BluetoothProperty::DynamicAudioBuffer() => BtPropertyType::DynamicAudioBuffer,
            BluetoothProperty::RemoteIsCoordinatedSetMember(_) => {
                BtPropertyType::RemoteIsCoordinatedSetMember
            }
            BluetoothProperty::Appearance(_) => BtPropertyType::Appearance,
            BluetoothProperty::VendorProductInfo(_) => BtPropertyType::VendorProductInfo,
            BluetoothProperty::RemoteDeviceTimestamp() => BtPropertyType::RemoteDeviceTimestamp,
            BluetoothProperty::RemoteAddrType(_) => BtPropertyType::RemoteAddrType,
            BluetoothProperty::Unknown() => BtPropertyType::Unknown,
        }
    }

    fn get_len(&self) -> usize {
        match &*self {
            BluetoothProperty::BdName(name) => cmp::min(PROPERTY_NAME_MAX, name.len() + 1),
            BluetoothProperty::BdAddr(addr) => addr.address.len(),
            BluetoothProperty::Uuids(uulist) => uulist.len() * mem::size_of::<Uuid>(),
            BluetoothProperty::ClassOfDevice(_) => mem::size_of::<u32>(),
            BluetoothProperty::TypeOfDevice(_) => mem::size_of::<BtDeviceType>(),
            BluetoothProperty::ServiceRecord(rec) => {
                mem::size_of::<BtServiceRecord>() + cmp::min(PROPERTY_NAME_MAX, rec.name.len() + 1)
            }
            BluetoothProperty::AdapterScanMode(_) => mem::size_of::<BtScanMode>(),
            BluetoothProperty::AdapterBondedDevices(devlist) => {
                devlist.len() * mem::size_of::<RawAddress>()
            }
            BluetoothProperty::AdapterDiscoverableTimeout(_) => mem::size_of::<u32>(),
            BluetoothProperty::RemoteFriendlyName(name) => {
                cmp::min(PROPERTY_NAME_MAX, name.len() + 1)
            }
            BluetoothProperty::RemoteRssi(_) => mem::size_of::<i8>(),
            BluetoothProperty::RemoteVersionInfo(_) => mem::size_of::<BtRemoteVersion>(),
            BluetoothProperty::LocalLeFeatures(_) => mem::size_of::<BtLocalLeFeatures>(),
            BluetoothProperty::LocalIoCaps(_) => mem::size_of::<BtIoCap>(),
            BluetoothProperty::LocalIoCapsBle(_) => mem::size_of::<BtIoCap>(),
            BluetoothProperty::RemoteIsCoordinatedSetMember(_) => mem::size_of::<bool>(),
            BluetoothProperty::Appearance(_) => mem::size_of::<u16>(),
            BluetoothProperty::VendorProductInfo(_) => mem::size_of::<BtVendorProductInfo>(),
            BluetoothProperty::RemoteAddrType(_) => mem::size_of::<BtAddrType>(),

            // TODO(abps) - Figure out sizes for these
            BluetoothProperty::DynamicAudioBuffer() => 0,
            BluetoothProperty::RemoteDeviceTimestamp() => 0,
            BluetoothProperty::Unknown() => 0,
        }
    }

    /// Given a mutable array, this will copy the data to that array and return a
    /// LTCheckedPtrMut to it.
    ///
    /// The lifetime of the returned pointer is tied to that of the slice given.
    fn get_data_ptr<'a>(&'a self, data: &'a mut [u8]) -> LTCheckedPtrMut<'a, u8> {
        let len = self.get_len();
        match &*self {
            BluetoothProperty::BdName(name) => {
                let copy_len = len - 1;
                data[0..copy_len].copy_from_slice(&name.as_bytes()[0..copy_len]);
                data[copy_len] = 0;
            }
            BluetoothProperty::BdAddr(addr) => {
                data.copy_from_slice(&addr.address);
            }
            BluetoothProperty::Uuids(uulist) => {
                for (idx, &uuid) in uulist.iter().enumerate() {
                    let start = idx * mem::size_of::<Uuid>();
                    let end = start + mem::size_of::<Uuid>();
                    data[start..end].copy_from_slice(&uuid.uu);
                }
            }
            BluetoothProperty::ClassOfDevice(cod) => {
                data.copy_from_slice(&cod.to_ne_bytes());
            }
            BluetoothProperty::TypeOfDevice(tod) => {
                data.copy_from_slice(&BtDeviceType::to_u32(tod).unwrap_or_default().to_ne_bytes());
            }
            BluetoothProperty::ServiceRecord(sr) => {
                // Do an unsafe cast to binding:: type and assign the values
                // The underlying memory location is provided by |data| which will
                // have enough space because it uses get_len()
                let record =
                    unsafe { &mut *(data.as_mut_ptr() as *mut bindings::bt_service_record_t) };
                record.uuid = sr.uuid;
                record.channel = sr.channel;
                let name_len = len - mem::size_of::<BtServiceRecord>() - 1;
                record.name[0..name_len].copy_from_slice(
                    &(sr.name.as_bytes().iter().map(|x| *x as c_char).collect::<Vec<c_char>>())
                        [0..name_len],
                );
                record.name[name_len] = 0;
            }
            BluetoothProperty::AdapterScanMode(sm) => {
                data.copy_from_slice(&BtScanMode::to_u32(sm).unwrap_or_default().to_ne_bytes());
            }
            BluetoothProperty::AdapterBondedDevices(devlist) => {
                for (idx, &dev) in devlist.iter().enumerate() {
                    let start = idx * mem::size_of::<RawAddress>();
                    let end = idx + mem::size_of::<RawAddress>();
                    data[start..end].copy_from_slice(&dev.address);
                }
            }
            BluetoothProperty::AdapterDiscoverableTimeout(timeout) => {
                data.copy_from_slice(&timeout.to_ne_bytes());
            }
            BluetoothProperty::RemoteFriendlyName(name) => {
                let copy_len = len - 1;
                data[0..copy_len].copy_from_slice(&name.as_bytes()[0..copy_len]);
                data[copy_len] = 0;
            }
            BluetoothProperty::RemoteRssi(rssi) => {
                data[0] = *rssi as u8;
            }
            BluetoothProperty::RemoteVersionInfo(rvi) => {
                let ptr: *const BtRemoteVersion = rvi;
                let slice = unsafe {
                    std::slice::from_raw_parts(ptr as *mut u8, mem::size_of::<BtRemoteVersion>())
                };
                data.copy_from_slice(&slice);
            }
            BluetoothProperty::LocalLeFeatures(llf) => {
                let ptr: *const BtLocalLeFeatures = llf;
                let slice = unsafe {
                    std::slice::from_raw_parts(ptr as *mut u8, mem::size_of::<BtLocalLeFeatures>())
                };
                data.copy_from_slice(&slice);
            }
            BluetoothProperty::LocalIoCaps(iocap) => {
                data.copy_from_slice(&BtIoCap::to_u32(iocap).unwrap_or_default().to_ne_bytes());
            }
            BluetoothProperty::LocalIoCapsBle(iocap) => {
                data.copy_from_slice(&BtIoCap::to_u32(iocap).unwrap_or_default().to_ne_bytes());
            }
            BluetoothProperty::RemoteIsCoordinatedSetMember(icsm) => {
                data[0] = *icsm as u8;
            }
            BluetoothProperty::Appearance(appearance) => {
                data.copy_from_slice(&appearance.to_ne_bytes());
            }
            BluetoothProperty::VendorProductInfo(vpi) => {
                let ptr: *const BtVendorProductInfo = vpi;
                let slice = unsafe {
                    std::slice::from_raw_parts(
                        ptr as *mut u8,
                        mem::size_of::<BtVendorProductInfo>(),
                    )
                };
                data.copy_from_slice(&slice);
            }
            BluetoothProperty::RemoteAddrType(addr_type) => {
                data.copy_from_slice(
                    &BtAddrType::to_u32(addr_type).unwrap_or_default().to_ne_bytes(),
                );
            }

            BluetoothProperty::DynamicAudioBuffer() => (),
            BluetoothProperty::RemoteDeviceTimestamp() => (),
            BluetoothProperty::Unknown() => (),
        };

        data.into()
    }
}

// TODO(abps) - Check that sizes are correct when given a BtProperty
impl From<bindings::bt_property_t> for BluetoothProperty {
    fn from(prop: bindings::bt_property_t) -> Self {
        let slice: &[u8] =
            unsafe { std::slice::from_raw_parts(prop.val as *mut u8, prop.len as usize) };
        let len = prop.len as usize;

        match BtPropertyType::from(prop.type_) {
            BtPropertyType::BdName => BluetoothProperty::BdName(ascii_to_string(slice, len)),
            BtPropertyType::BdAddr => {
                BluetoothProperty::BdAddr(RawAddress::from_bytes(slice).unwrap_or_default())
            }
            BtPropertyType::Uuids => {
                let count = len / mem::size_of::<Uuid>();
                BluetoothProperty::Uuids(ptr_to_vec(prop.val as *const Uuid, count))
            }
            BtPropertyType::ClassOfDevice => {
                BluetoothProperty::ClassOfDevice(u32_from_bytes(slice))
            }
            BtPropertyType::TypeOfDevice => BluetoothProperty::TypeOfDevice(
                BtDeviceType::from_u32(u32_from_bytes(slice)).unwrap_or(BtDeviceType::Unknown),
            ),
            BtPropertyType::ServiceRecord => {
                let v =
                    unsafe { (prop.val as *const bindings::bt_service_record_t).read_unaligned() };
                BluetoothProperty::ServiceRecord(BtServiceRecord::from(v))
            }
            BtPropertyType::AdapterScanMode => BluetoothProperty::AdapterScanMode(
                BtScanMode::from_u32(u32_from_bytes(slice)).unwrap_or(BtScanMode::None_),
            ),
            BtPropertyType::AdapterBondedDevices => {
                let count = len / mem::size_of::<RawAddress>();
                BluetoothProperty::AdapterBondedDevices(ptr_to_vec(
                    prop.val as *const RawAddress,
                    count,
                ))
            }
            BtPropertyType::AdapterDiscoverableTimeout => {
                BluetoothProperty::AdapterDiscoverableTimeout(u32_from_bytes(slice))
            }
            BtPropertyType::RemoteFriendlyName => {
                BluetoothProperty::RemoteFriendlyName(ascii_to_string(slice, len))
            }
            BtPropertyType::RemoteRssi => BluetoothProperty::RemoteRssi(slice[0] as i8),
            BtPropertyType::RemoteVersionInfo => {
                let v = unsafe { (prop.val as *const BtRemoteVersion).read_unaligned() };
                BluetoothProperty::RemoteVersionInfo(v.clone())
            }
            BtPropertyType::LocalLeFeatures => {
                let v = unsafe { (prop.val as *const BtLocalLeFeatures).read_unaligned() };
                BluetoothProperty::LocalLeFeatures(v.clone())
            }
            BtPropertyType::LocalIoCaps => BluetoothProperty::LocalIoCaps(
                BtIoCap::from_u32(u32_from_bytes(slice)).unwrap_or(BtIoCap::Unknown),
            ),
            BtPropertyType::LocalIoCapsBle => BluetoothProperty::LocalIoCapsBle(
                BtIoCap::from_u32(u32_from_bytes(slice)).unwrap_or(BtIoCap::Unknown),
            ),
            BtPropertyType::RemoteIsCoordinatedSetMember => {
                BluetoothProperty::RemoteIsCoordinatedSetMember(slice[0] != 0)
            }
            BtPropertyType::Appearance => BluetoothProperty::Appearance(u16_from_bytes(slice)),
            BtPropertyType::VendorProductInfo => {
                let v = unsafe { (prop.val as *const BtVendorProductInfo).read_unaligned() };
                BluetoothProperty::VendorProductInfo(BtVendorProductInfo::from(v))
            }
            BtPropertyType::RemoteAddrType => BluetoothProperty::RemoteAddrType(
                BtAddrType::from_u32(u32_from_bytes(slice)).unwrap_or(BtAddrType::Unknown),
            ),
            // TODO(abps) - Figure out if these values should actually have contents
            BtPropertyType::DynamicAudioBuffer => BluetoothProperty::DynamicAudioBuffer(),
            BtPropertyType::RemoteDeviceTimestamp => BluetoothProperty::RemoteDeviceTimestamp(),
            _ => BluetoothProperty::Unknown(),
        }
    }
}

impl From<BluetoothProperty> for (Box<[u8]>, bindings::bt_property_t) {
    fn from(prop: BluetoothProperty) -> Self {
        let dvec: Vec<u8> = vec![0; prop.get_len()];
        let mut data: Box<[u8]> = dvec.into_boxed_slice();
        let prop = bindings::bt_property_t {
            type_: prop.get_type().into(),
            len: prop.get_len() as i32,
            val: prop.get_data_ptr(&mut data).cast_into::<std::os::raw::c_void>(),
        };

        (data, prop)
    }
}

pub enum SupportedProfiles {
    HidHost,
    Hfp,
    A2dp,
    Gatt,
    Sdp,
    Socket,
    HfClient,
    AvrcpCtrl,
}

impl From<SupportedProfiles> for Vec<u8> {
    fn from(item: SupportedProfiles) -> Self {
        match item {
            SupportedProfiles::HidHost => "hidhost",
            SupportedProfiles::Hfp => "handsfree",
            SupportedProfiles::A2dp => "a2dp",
            SupportedProfiles::Gatt => "gatt",
            SupportedProfiles::Sdp => "sdp",
            SupportedProfiles::Socket => "socket",
            SupportedProfiles::HfClient => "handsfree_client",
            SupportedProfiles::AvrcpCtrl => "avrcp_ctrl",
        }
        .bytes()
        .chain("\0".bytes())
        .collect::<Vec<u8>>()
    }
}

#[cxx::bridge(namespace = bluetooth::topshim::rust)]
mod ffi {
    unsafe extern "C++" {
        include!("btif/btif_shim.h");

        // For converting init flags from Vec<String> to const char **
        type InitFlags;

        // Convert flgas into an InitFlags object
        fn ConvertFlags(flags: Vec<String>) -> UniquePtr<InitFlags>;
        fn GetFlagsPtr(self: &InitFlags) -> *mut *const c_char;
    }
}

/// The RawAddress directly exported from the bindings.
///
/// To make use of RawAddress in cxx::bridge C++ blocks,
/// include the following snippet in the ffi module.
/// ```ignore
/// #[cxx::bridge(namespace = bluetooth::topshim::rust)]
/// mod ffi {
///     unsafe extern "C++" {
///         include!("gd/rust/topshim/common/type_alias.h");
///         type RawAddress = crate::btif::RawAddress;
///     }
///     // Place you shared stuff here.
/// }
/// ```
pub type RawAddress = bindings::RawAddress;
pub type OobData = bindings::bt_oob_data_s;

unsafe impl ExternType for RawAddress {
    type Id = type_id!("bluetooth::topshim::rust::RawAddress");
    type Kind = cxx::kind::Trivial;
}

impl Hash for RawAddress {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.address.hash(state);
    }
}

// TODO (b/264603574): Handling address hiding in rust logging statements
impl ToString for RawAddress {
    fn to_string(&self) -> String {
        String::from(format!(
            "{:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
            self.address[0],
            self.address[1],
            self.address[2],
            self.address[3],
            self.address[4],
            self.address[5]
        ))
    }
}

impl RawAddress {
    /// Constructs a RawAddress from a slice of 6 bytes.
    pub fn from_bytes(raw_addr: &[u8]) -> Option<RawAddress> {
        if raw_addr.len() != 6 {
            return None;
        }
        let mut raw: [u8; 6] = [0; 6];
        raw.copy_from_slice(raw_addr);
        return Some(RawAddress { address: raw });
    }

    pub fn from_string<S: Into<String>>(addr: S) -> Option<RawAddress> {
        let addr: String = addr.into();
        let s = addr.split(':').collect::<Vec<&str>>();

        if s.len() != 6 {
            return None;
        }

        let mut raw: [u8; 6] = [0; 6];
        for i in 0..s.len() {
            raw[i] = match u8::from_str_radix(s[i], 16) {
                Ok(res) => res,
                Err(_) => {
                    return None;
                }
            };
        }

        Some(RawAddress { address: raw })
    }

    pub fn to_byte_arr(&self) -> [u8; 6] {
        self.address.clone()
    }

    pub fn empty() -> RawAddress {
        unsafe { bindings::RawAddress_kEmpty }
    }
}

/// Address that is safe to display in logs.
pub struct DisplayAddress<'a>(pub &'a RawAddress);
impl<'a> Display for DisplayAddress<'a> {
    fn fmt(&self, f: &mut Formatter) -> Result {
        write!(f, "xx:xx:xx:xx:{:02X}:{:02X}", &self.0.address[4], &self.0.address[5])
    }
}

/// An enum representing `bt_callbacks_t` from btif.
#[derive(Clone, Debug)]
pub enum BaseCallbacks {
    AdapterState(BtState),
    AdapterProperties(BtStatus, i32, Vec<BluetoothProperty>),
    RemoteDeviceProperties(BtStatus, RawAddress, i32, Vec<BluetoothProperty>),
    DeviceFound(i32, Vec<BluetoothProperty>),
    DiscoveryState(BtDiscoveryState),
    PinRequest(RawAddress, String, u32, bool),
    SspRequest(RawAddress, String, u32, BtSspVariant, u32),
    BondState(BtStatus, RawAddress, BtBondState, i32),
    AddressConsolidate(RawAddress, RawAddress),
    LeAddressAssociate(RawAddress, RawAddress),
    AclState(
        BtStatus,
        RawAddress,
        BtAclState,
        BtTransport,
        BtHciErrorCode,
        BtConnectionDirection,
        u16,
    ),
    ThreadEvent(BtThreadEvent),
    // Unimplemented so far:
    // dut_mode_recv_cb
    // le_test_mode_cb
    // energy_info_cb
    // link_quality_report_cb
    // switch_buffer_size_cb
    // switch_codec_cb
    GenerateLocalOobData(u8, OobData),
    LeRandCallback(u64),
    // key_missing_cb
}

pub struct BaseCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(BaseCallbacks) + Send>,
}

type BaseCb = Arc<Mutex<BaseCallbacksDispatcher>>;

cb_variant!(BaseCb, adapter_state_cb -> BaseCallbacks::AdapterState, u32 -> BtState);
cb_variant!(BaseCb, adapter_properties_cb -> BaseCallbacks::AdapterProperties,
u32 -> BtStatus, i32, *mut bindings::bt_property_t, {
    let _2 = ptr_to_vec(_2, _1 as usize);
});
cb_variant!(BaseCb, remote_device_properties_cb -> BaseCallbacks::RemoteDeviceProperties,
u32 -> BtStatus, *mut RawAddress -> RawAddress, i32, *mut bindings::bt_property_t, {
    let _1 = unsafe { *(_1 as *const RawAddress) };
    let _3 = ptr_to_vec(_3, _2 as usize);
});
cb_variant!(BaseCb, device_found_cb -> BaseCallbacks::DeviceFound,
i32, *mut bindings::bt_property_t, {
    let _1 = ptr_to_vec(_1, _0 as usize);
});
cb_variant!(BaseCb, discovery_state_cb -> BaseCallbacks::DiscoveryState,
    bindings::bt_discovery_state_t -> BtDiscoveryState);
cb_variant!(BaseCb, pin_request_cb -> BaseCallbacks::PinRequest,
*mut RawAddress, *mut bindings::bt_bdname_t, u32, bool, {
    let _0 = unsafe { *(_0 as *const RawAddress)};
    let _1 = String::from(unsafe{*_1});
});
cb_variant!(BaseCb, ssp_request_cb -> BaseCallbacks::SspRequest,
*mut RawAddress, *mut bindings::bt_bdname_t, u32, bindings::bt_ssp_variant_t -> BtSspVariant, u32, {
    let _0 = unsafe { *(_0 as *const RawAddress) };
    let _1 = String::from(unsafe{*_1});
});
cb_variant!(BaseCb, bond_state_cb -> BaseCallbacks::BondState,
u32 -> BtStatus, *mut RawAddress, bindings::bt_bond_state_t -> BtBondState, i32, {
    let _1 = unsafe { *(_1 as *const RawAddress) };
});

cb_variant!(BaseCb, address_consolidate_cb -> BaseCallbacks::AddressConsolidate,
*mut RawAddress, *mut RawAddress, {
    let _0 = unsafe { *(_0 as *const RawAddress) };
    let _1 = unsafe { *(_1 as *const RawAddress) };
});

cb_variant!(BaseCb, le_address_associate_cb -> BaseCallbacks::LeAddressAssociate,
*mut RawAddress, *mut RawAddress, {
    let _0 = unsafe { *(_0 as *const RawAddress) };
    let _1 = unsafe { *(_1 as *const RawAddress) };
});

cb_variant!(BaseCb, thread_evt_cb -> BaseCallbacks::ThreadEvent, u32 -> BtThreadEvent);

cb_variant!(BaseCb, acl_state_cb -> BaseCallbacks::AclState,
u32 -> BtStatus, *mut RawAddress, bindings::bt_acl_state_t -> BtAclState, i32 -> BtTransport, bindings::bt_hci_error_code_t -> BtHciErrorCode, bindings::bt_conn_direction_t -> BtConnectionDirection, u16 -> u16, {
    let _1 = unsafe { *(_1 as *const RawAddress) };
});

cb_variant!(BaseCb, generate_local_oob_data_cb -> BaseCallbacks::GenerateLocalOobData, u8, OobData);

cb_variant!(BaseCb, le_rand_cb -> BaseCallbacks::LeRandCallback, u64);

struct RawInterfaceWrapper {
    pub raw: *const bindings::bt_interface_t,
}

unsafe impl Send for RawInterfaceWrapper {}

/// Macro to call functions via function pointers. Expects the self object to
/// have a raw interface wrapper at `self.internal`. The actual function call is
/// marked unsafe since it will need to dereference a C object. This can cause
/// segfaults if not validated beforehand.
///
/// Example:
///     ccall!(self, foobar, arg1, arg2)
///     Expands to: unsafe {((*self.internal.raw).foobar.unwrap())(arg1, arg2)}
#[macro_export]
macro_rules! ccall {
    ($self:ident,$fn_name:ident) => {
        unsafe {
            ((*$self.internal.raw).$fn_name.unwrap())()
        }
    };
    ($self:ident,$fn_name:ident, $($args:expr),*) => {
        unsafe {
            ((*$self.internal.raw).$fn_name.unwrap())($($args),*)
        }
    };
}

/// Macro to call const functions via cxx. Expects the self object to have the
/// cxx object to be called at `self.internal_cxx`.
///
/// Example:
///     cxxcall!(self, foobar, arg1, arg2)
///     Expands to: self.internal_cxx.foobar(arg1, arg2)
#[macro_export]
macro_rules! cxxcall {
    ($self:expr,$fn_name:ident) => {
        $self.internal_cxx.$fn_name()
    };
    ($self:expr,$fn_name:ident, $($args:expr),*) => {
        $self.internal_cxx.$fn_name($($args),*)
    };
}

/// Macro to call mutable functions via cxx. Mutable functions are always
/// required to be defined with `self: Pin<&mut Self>`. The self object must
/// have the cxx object at `self.internal_cxx`.
///
/// Example:
///     mutcxxcall!(self, foobar, arg1, arg2)
///     Expands to: self.internal_cxx.pin_mut().foobar(arg1, arg2)
#[macro_export]
macro_rules! mutcxxcall {
    ($self:expr,$fn_name:ident) => {
        $self.internal_cxx.pin_mut().$fn_name()
    };
    ($self:expr,$fn_name:ident, $($args:expr),*) => {
        $self.internal_cxx.pin_mut().$fn_name($($args),*)
    };
}

#[no_mangle]
extern "C" fn wake_lock_noop(_0: *const ::std::os::raw::c_char) -> ::std::os::raw::c_int {
    // The wakelock mechanism is not available on this platform,
    // so just returning success to avoid error log.
    0
}

/// Rust wrapper around `bt_interface_t`.
pub struct BluetoothInterface {
    internal: RawInterfaceWrapper,

    /// Set to true after `initialize` is called.
    pub is_init: bool,

    // Need to take ownership of callbacks so it doesn't get freed after init
    callbacks: Option<Box<bindings::bt_callbacks_t>>,
    os_callouts: Option<Box<bindings::bt_os_callouts_t>>,
}

impl BluetoothInterface {
    pub fn is_initialized(&self) -> bool {
        self.is_init
    }

    /// Initialize the Bluetooth interface by setting up the underlying interface.
    ///
    /// # Arguments
    ///
    /// * `callbacks` - Dispatcher struct that accepts [`BaseCallbacks`]
    /// * `init_flags` - List of flags sent to libbluetooth for init.
    pub fn initialize(
        &mut self,
        callbacks: BaseCallbacksDispatcher,
        init_flags: Vec<String>,
    ) -> bool {
        // Init flags need to be converted from string to null terminated bytes
        let converted: cxx::UniquePtr<ffi::InitFlags> = ffi::ConvertFlags(init_flags);
        let flags = (*converted).GetFlagsPtr();

        if get_dispatchers().lock().unwrap().set::<BaseCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for BaseCallbacks but it already existed");
        }

        // Fill up callbacks struct to pass to init function (will be copied so
        // no need to worry about ownership)
        let mut callbacks = Box::new(bindings::bt_callbacks_t {
            size: std::mem::size_of::<bindings::bt_callbacks_t>(),
            adapter_state_changed_cb: Some(adapter_state_cb),
            adapter_properties_cb: Some(adapter_properties_cb),
            remote_device_properties_cb: Some(remote_device_properties_cb),
            device_found_cb: Some(device_found_cb),
            discovery_state_changed_cb: Some(discovery_state_cb),
            pin_request_cb: Some(pin_request_cb),
            ssp_request_cb: Some(ssp_request_cb),
            bond_state_changed_cb: Some(bond_state_cb),
            address_consolidate_cb: Some(address_consolidate_cb),
            le_address_associate_cb: Some(le_address_associate_cb),
            acl_state_changed_cb: Some(acl_state_cb),
            thread_evt_cb: Some(thread_evt_cb),
            dut_mode_recv_cb: None,
            le_test_mode_cb: None,
            energy_info_cb: None,
            link_quality_report_cb: None,
            generate_local_oob_data_cb: Some(generate_local_oob_data_cb),
            switch_buffer_size_cb: None,
            switch_codec_cb: None,
            le_rand_cb: Some(le_rand_cb),
            key_missing_cb: None,
        });

        let cb_ptr = LTCheckedPtrMut::from(&mut callbacks);

        let (guest_mode, is_common_criteria_mode, config_compare_result, is_atv) =
            (false, false, 0, false);

        let init = ccall!(
            self,
            init,
            cb_ptr.into(),
            guest_mode,
            is_common_criteria_mode,
            config_compare_result,
            flags,
            is_atv,
            std::ptr::null()
        );

        self.is_init = init == 0;
        self.callbacks = Some(callbacks);

        if self.is_init {
            // Fill up OSI function table and register it with BTIF.
            // TODO(b/271931441) - pass a NoOpOsCallouts structure from
            // gd/rust/linux/stack.
            let mut callouts = Box::new(bindings::bt_os_callouts_t {
                size: std::mem::size_of::<bindings::bt_os_callouts_t>(),
                acquire_wake_lock: Some(wake_lock_noop),
                release_wake_lock: Some(wake_lock_noop),
            });
            let callouts_ptr = LTCheckedPtrMut::from(&mut callouts);
            ccall!(self, set_os_callouts, callouts_ptr.into());
            self.os_callouts = Some(callouts);
        }

        return self.is_init;
    }

    pub fn cleanup(&self) {
        ccall!(self, cleanup)
    }

    pub fn enable(&self) -> i32 {
        ccall!(self, enable)
    }

    pub fn disable(&self) -> i32 {
        ccall!(self, disable)
    }

    pub fn get_adapter_properties(&self) -> i32 {
        ccall!(self, get_adapter_properties)
    }

    pub fn get_adapter_property(&self, prop: BtPropertyType) -> i32 {
        let converted_type = bindings::bt_property_type_t::from(prop);
        ccall!(self, get_adapter_property, converted_type)
    }

    pub fn set_adapter_property(&self, prop: BluetoothProperty) -> i32 {
        let prop_pair: (Box<[u8]>, bindings::bt_property_t) = prop.into();
        let prop_ptr = LTCheckedPtr::from_ref(&prop_pair.1);
        ccall!(self, set_adapter_property, prop_ptr.into())
    }

    pub fn get_remote_device_properties(&self, addr: &mut RawAddress) -> i32 {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        ccall!(self, get_remote_device_properties, addr_ptr.into())
    }

    pub fn get_remote_device_property(
        &self,
        addr: &mut RawAddress,
        prop_type: BtPropertyType,
    ) -> i32 {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        let converted_type = bindings::bt_property_type_t::from(prop_type);
        ccall!(self, get_remote_device_property, addr_ptr.into(), converted_type)
    }

    pub fn set_remote_device_property(
        &self,
        addr: &mut RawAddress,
        prop: BluetoothProperty,
    ) -> i32 {
        let prop_pair: (Box<[u8]>, bindings::bt_property_t) = prop.into();
        let prop_ptr = LTCheckedPtr::from_ref(&prop_pair.1);
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        ccall!(self, set_remote_device_property, addr_ptr.into(), prop_ptr.into())
    }

    pub fn get_remote_services(&self, addr: &mut RawAddress, transport: BtTransport) -> i32 {
        let addr_ptr = LTCheckedPtrMut::from_ref(addr);
        ccall!(self, get_remote_services, addr_ptr.into(), transport.to_i32().unwrap())
    }

    pub fn start_discovery(&self) -> i32 {
        ccall!(self, start_discovery)
    }

    pub fn cancel_discovery(&self) -> i32 {
        ccall!(self, cancel_discovery)
    }

    pub fn create_bond(&self, addr: &RawAddress, transport: BtTransport) -> i32 {
        let ctransport: i32 = transport.into();
        let addr_ptr = LTCheckedPtr::from_ref(addr);
        ccall!(self, create_bond, addr_ptr.into(), ctransport)
    }

    pub fn remove_bond(&self, addr: &RawAddress) -> i32 {
        let addr_ptr = LTCheckedPtr::from_ref(addr);
        ccall!(self, remove_bond, addr_ptr.into())
    }

    pub fn cancel_bond(&self, addr: &RawAddress) -> i32 {
        let addr_ptr = LTCheckedPtr::from_ref(addr);
        ccall!(self, cancel_bond, addr_ptr.into())
    }

    pub fn get_connection_state(&self, addr: &RawAddress) -> BtConnectionState {
        let addr_ptr = LTCheckedPtr::from_ref(addr);
        ccall!(self, get_connection_state, addr_ptr.into()).into()
    }

    pub fn pin_reply(
        &self,
        addr: &RawAddress,
        accept: u8,
        pin_len: u8,
        pin_code: &mut BtPinCode,
    ) -> i32 {
        let addr_ptr = LTCheckedPtr::from_ref(addr);
        let pin_code_ptr = LTCheckedPtrMut::from_ref(pin_code);
        ccall!(self, pin_reply, addr_ptr.into(), accept, pin_len, pin_code_ptr.into())
    }

    pub fn ssp_reply(
        &self,
        addr: &RawAddress,
        variant: BtSspVariant,
        accept: u8,
        passkey: u32,
    ) -> i32 {
        let addr_ptr = LTCheckedPtr::from_ref(addr);
        let cvariant = bindings::bt_ssp_variant_t::from(variant);
        ccall!(self, ssp_reply, addr_ptr.into(), cvariant, accept, passkey)
    }

    pub fn clear_event_filter(&self) -> i32 {
        ccall!(self, clear_event_filter)
    }

    pub fn clear_event_mask(&self) -> i32 {
        ccall!(self, clear_event_mask)
    }

    pub fn clear_filter_accept_list(&self) -> i32 {
        ccall!(self, clear_filter_accept_list)
    }

    pub fn disconnect_all_acls(&self) -> i32 {
        ccall!(self, disconnect_all_acls)
    }

    pub fn allow_wake_by_hid(&self) -> i32 {
        ccall!(self, allow_wake_by_hid)
    }

    pub fn get_wbs_supported(&self) -> bool {
        ccall!(self, get_wbs_supported)
    }

    pub fn get_swb_supported(&self) -> bool {
        ccall!(self, get_swb_supported)
    }

    pub fn is_coding_format_supported(&self, coding_format: u8) -> bool {
        ccall!(self, is_coding_format_supported, coding_format)
    }

    pub fn le_rand(&self) -> i32 {
        ccall!(self, le_rand)
    }

    pub fn generate_local_oob_data(&self, transport: i32) -> i32 {
        ccall!(self, generate_local_oob_data, transport as u8)
    }

    pub fn restore_filter_accept_list(&self) -> i32 {
        ccall!(self, restore_filter_accept_list)
    }

    pub fn set_default_event_mask_except(&self, mask: u64, le_mask: u64) -> i32 {
        ccall!(self, set_default_event_mask_except, mask, le_mask)
    }

    pub fn set_event_filter_inquiry_result_all_devices(&self) -> i32 {
        ccall!(self, set_event_filter_inquiry_result_all_devices)
    }

    pub fn set_event_filter_connection_setup_all_devices(&self) -> i32 {
        ccall!(self, set_event_filter_connection_setup_all_devices)
    }

    pub(crate) fn get_profile_interface(
        &self,
        profile: SupportedProfiles,
    ) -> *const std::os::raw::c_void {
        let cprofile = Vec::<u8>::from(profile);
        let cprofile_ptr = LTCheckedPtr::from(&cprofile);
        ccall!(self, get_profile_interface, cprofile_ptr.cast_into::<std::os::raw::c_char>())
    }

    pub(crate) fn as_raw_ptr(&self) -> *const u8 {
        self.internal.raw as *const u8
    }
}

pub trait ToggleableProfile {
    fn is_enabled(&self) -> bool;
    fn enable(&mut self) -> bool;
    fn disable(&mut self) -> bool;
}

pub fn get_btinterface() -> Option<BluetoothInterface> {
    let mut ret: Option<BluetoothInterface> = None;
    let mut ifptr: *const bindings::bt_interface_t = std::ptr::null();

    unsafe {
        if bindings::hal_util_load_bt_library(&mut ifptr) == 0 {
            ret = Some(BluetoothInterface {
                internal: RawInterfaceWrapper { raw: ifptr },
                is_init: false,
                callbacks: None,
                os_callouts: None,
            });
        }
    }

    ret
}

// Turns C-array T[] to Vec<U>.
pub(crate) fn ptr_to_vec<T: Copy, U: From<T>>(start: *const T, length: usize) -> Vec<U> {
    unsafe {
        (0..length).map(|i| U::from(start.offset(i as isize).read_unaligned())).collect::<Vec<U>>()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_bdname_from_slice(slice: &[u8]) -> bindings::bt_bdname_t {
        // Length of slice must be less than bd_name max
        assert!(slice.len() <= 249);

        let mut bdname = bindings::bt_bdname_t { name: [128; 249] };

        for (i, v) in slice.iter().enumerate() {
            bdname.name[i] = v.clone();
        }

        bdname
    }

    #[test]
    fn test_bdname_conversions() {
        let hello_bdname = make_bdname_from_slice(&[72, 69, 76, 76, 79, 0]);
        assert_eq!("HELLO".to_string(), String::from(hello_bdname));

        let empty_bdname = make_bdname_from_slice(&[0]);
        assert_eq!("".to_string(), String::from(empty_bdname));

        let no_nullterm_bdname = make_bdname_from_slice(&[72, 69, 76, 76, 79]);
        assert_eq!("".to_string(), String::from(no_nullterm_bdname));

        let invalid_bdname = make_bdname_from_slice(&[128; 249]);
        assert_eq!("".to_string(), String::from(invalid_bdname));
    }

    #[test]
    fn test_ptr_to_vec() {
        let arr: [i32; 3] = [1, 2, 3];
        let vec: Vec<i32> = ptr_to_vec(arr.as_ptr(), arr.len());
        let expected: Vec<i32> = vec![1, 2, 3];
        assert_eq!(expected, vec);
    }

    #[test]
    fn test_property_with_string_conversions() {
        {
            let bdname = BluetoothProperty::BdName("FooBar".into());
            let prop_pair: (Box<[u8]>, bindings::bt_property_t) = bdname.into();
            let converted: BluetoothProperty = prop_pair.1.into();
            assert!(match converted {
                BluetoothProperty::BdName(name) => "FooBar".to_string() == name,
                _ => false,
            });
        }

        {
            let orig_record = BtServiceRecord {
                uuid: Uuid::from([0; 16]),
                channel: 3,
                name: "FooBar".to_string(),
            };
            let service_record = BluetoothProperty::ServiceRecord(orig_record.clone());
            let prop_pair: (Box<[u8]>, bindings::bt_property_t) = service_record.into();
            let converted: BluetoothProperty = prop_pair.1.into();
            assert!(match converted {
                BluetoothProperty::ServiceRecord(sr) => {
                    sr.uuid == orig_record.uuid
                        && sr.channel == orig_record.channel
                        && sr.name == orig_record.name
                }
                _ => false,
            });
        }

        {
            let rfname = BluetoothProperty::RemoteFriendlyName("FooBizz".into());
            let prop_pair: (Box<[u8]>, bindings::bt_property_t) = rfname.into();
            let converted: BluetoothProperty = prop_pair.1.into();
            assert!(match converted {
                BluetoothProperty::RemoteFriendlyName(name) => "FooBizz".to_string() == name,
                _ => false,
            });
        }
    }

    #[test]
    fn test_display_address() {
        assert_eq!(
            format!("{}", DisplayAddress(&RawAddress::from_string("00:00:00:00:00:00").unwrap())),
            String::from("xx:xx:xx:xx:00:00")
        );
        assert_eq!(
            format!("{}", DisplayAddress(&RawAddress::from_string("1a:2b:1a:2b:1a:2b").unwrap())),
            String::from("xx:xx:xx:xx:1A:2B")
        );
        assert_eq!(
            format!("{}", DisplayAddress(&RawAddress::from_string("3C:4D:3C:4D:3C:4D").unwrap())),
            String::from("xx:xx:xx:xx:3C:4D")
        );
        assert_eq!(
            format!("{}", DisplayAddress(&RawAddress::from_string("11:35:11:35:11:35").unwrap())),
            String::from("xx:xx:xx:xx:11:35")
        );
    }
}
