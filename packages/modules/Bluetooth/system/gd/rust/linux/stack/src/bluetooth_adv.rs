//! BLE Advertising types and utilities

use btif_macros::{btif_callback, btif_callbacks_dispatcher};

use bt_topshim::btif::{RawAddress, Uuid};
use bt_topshim::profiles::gatt::{AdvertisingStatus, Gatt, GattAdvCallbacks, LePhy};

use itertools::Itertools;
use log::{debug, error, info, warn};
use num_traits::clamp;
use std::collections::{HashMap, VecDeque};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tokio::sync::mpsc::Sender;
use tokio::task::JoinHandle;
use tokio::time;

use crate::bluetooth::{Bluetooth, IBluetooth};
use crate::callbacks::Callbacks;
use crate::uuid::UuidHelper;
use crate::{Message, RPCProxy, SuspendMode};

pub type AdvertiserId = i32;
pub type CallbackId = u32;
pub type RegId = i32;
pub type ManfId = u16;

/// Advertising parameters for each BLE advertising set.
#[derive(Debug, Default, Clone)]
pub struct AdvertisingSetParameters {
    /// Whether the advertisement will be connectable.
    pub connectable: bool,
    /// Whether the advertisement will be scannable.
    pub scannable: bool,
    /// Whether the legacy advertisement will be used.
    pub is_legacy: bool,
    /// Whether the advertisement will be anonymous.
    pub is_anonymous: bool,
    /// Whether the TX Power will be included.
    pub include_tx_power: bool,
    /// Primary advertising phy. Valid values are: 1 (1M), 2 (2M), 3 (Coded).
    pub primary_phy: LePhy,
    /// Secondary advertising phy. Valid values are: 1 (1M), 2 (2M), 3 (Coded).
    pub secondary_phy: LePhy,
    /// The advertising interval. Bluetooth LE Advertising interval, in 0.625 ms unit.
    /// The valid range is from 160 (100 ms) to 16777215 (10485.759375 sec).
    /// Recommended values are: 160 (100 ms), 400 (250 ms), 1600 (1 sec).
    pub interval: i32,
    /// Transmission power of Bluetooth LE Advertising, in dBm. The valid range is [-127, 1].
    /// Recommended values are: -21, -15, 7, 1.
    pub tx_power_level: i32,
    /// Own address type for advertising to control public or privacy mode.
    /// The valid types are: -1 (default), 0 (public), 1 (random).
    pub own_address_type: i32,
}

/// Represents the data to be advertised and the scan response data for active scans.
#[derive(Debug, Default, Clone)]
pub struct AdvertiseData {
    /// A list of service UUIDs within the advertisement that are used to identify
    /// the Bluetooth GATT services.
    pub service_uuids: Vec<Uuid>,
    /// A list of service solicitation UUIDs within the advertisement that we invite to connect.
    pub solicit_uuids: Vec<Uuid>,
    /// A list of transport discovery data.
    pub transport_discovery_data: Vec<Vec<u8>>,
    /// A collection of manufacturer Id and the corresponding manufacturer specific data.
    pub manufacturer_data: HashMap<ManfId, Vec<u8>>,
    /// A map of 128-bit UUID and its corresponding service data.
    pub service_data: HashMap<String, Vec<u8>>,
    /// Whether TX Power level will be included in the advertising packet.
    pub include_tx_power_level: bool,
    /// Whether the device name will be included in the advertisement packet.
    pub include_device_name: bool,
}

/// Parameters of the periodic advertising packet for BLE advertising set.
#[derive(Debug, Default)]
pub struct PeriodicAdvertisingParameters {
    /// Whether TX Power level will be included.
    pub include_tx_power: bool,
    /// Periodic advertising interval in 1.25 ms unit. Valid values are from 80 (100 ms) to
    /// 65519 (81.89875 sec). Value from range [interval, interval+20ms] will be picked as
    /// the actual value.
    pub interval: i32,
}

/// Interface for advertiser callbacks to clients, passed to
/// `IBluetoothGatt::start_advertising_set`.
pub trait IAdvertisingSetCallback: RPCProxy {
    /// Callback triggered in response to `start_advertising_set` indicating result of
    /// the operation.
    ///
    /// * `reg_id` - Identifies the advertising set registered by `start_advertising_set`.
    /// * `advertiser_id` - ID for the advertising set. It will be used in other advertising methods
    ///     and callbacks.
    /// * `tx_power` - Transmit power that will be used for this advertising set.
    /// * `status` - Status of this operation.
    fn on_advertising_set_started(
        &mut self,
        reg_id: i32,
        advertiser_id: i32,
        tx_power: i32,
        status: AdvertisingStatus,
    );

    /// Callback triggered in response to `get_own_address` indicating result of the operation.
    fn on_own_address_read(&mut self, advertiser_id: i32, address_type: i32, address: String);

    /// Callback triggered in response to `stop_advertising_set` indicating the advertising set
    /// is stopped.
    fn on_advertising_set_stopped(&mut self, advertiser_id: i32);

    /// Callback triggered in response to `enable_advertising_set` indicating result of
    /// the operation.
    fn on_advertising_enabled(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        status: AdvertisingStatus,
    );

    /// Callback triggered in response to `set_advertising_data` indicating result of the operation.
    fn on_advertising_data_set(&mut self, advertiser_id: i32, status: AdvertisingStatus);

    /// Callback triggered in response to `set_scan_response_data` indicating result of
    /// the operation.
    fn on_scan_response_data_set(&mut self, advertiser_id: i32, status: AdvertisingStatus);

    /// Callback triggered in response to `set_advertising_parameters` indicating result of
    /// the operation.
    fn on_advertising_parameters_updated(
        &mut self,
        advertiser_id: i32,
        tx_power: i32,
        status: AdvertisingStatus,
    );

    /// Callback triggered in response to `set_periodic_advertising_parameters` indicating result of
    /// the operation.
    fn on_periodic_advertising_parameters_updated(
        &mut self,
        advertiser_id: i32,
        status: AdvertisingStatus,
    );

    /// Callback triggered in response to `set_periodic_advertising_data` indicating result of
    /// the operation.
    fn on_periodic_advertising_data_set(&mut self, advertiser_id: i32, status: AdvertisingStatus);

    /// Callback triggered in response to `set_periodic_advertising_enable` indicating result of
    /// the operation.
    fn on_periodic_advertising_enabled(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        status: AdvertisingStatus,
    );

    /// When advertising module changes its suspend mode due to system suspend/resume.
    fn on_suspend_mode_change(&mut self, suspend_mode: SuspendMode);
}

// Advertising interval range.
const INTERVAL_MAX: i32 = 0xff_ffff; // 10485.759375 sec
const INTERVAL_MIN: i32 = 160; // 100 ms
const INTERVAL_DELTA: i32 = 50; // 31.25 ms gap between min and max

// Periodic advertising interval range.
const PERIODIC_INTERVAL_MAX: i32 = 65519; // 81.89875 sec
const PERIODIC_INTERVAL_MIN: i32 = 80; // 100 ms
const PERIODIC_INTERVAL_DELTA: i32 = 16; // 20 ms gap between min and max

// Device name length.
const DEVICE_NAME_MAX: usize = 26;

// Advertising data types.
const COMPLETE_LIST_16_BIT_SERVICE_UUIDS: u8 = 0x03;
const COMPLETE_LIST_32_BIT_SERVICE_UUIDS: u8 = 0x05;
const COMPLETE_LIST_128_BIT_SERVICE_UUIDS: u8 = 0x07;
const SHORTENED_LOCAL_NAME: u8 = 0x08;
const COMPLETE_LOCAL_NAME: u8 = 0x09;
const TX_POWER_LEVEL: u8 = 0x0a;
const LIST_16_BIT_SERVICE_SOLICITATION_UUIDS: u8 = 0x14;
const LIST_128_BIT_SERVICE_SOLICITATION_UUIDS: u8 = 0x15;
const SERVICE_DATA_16_BIT_UUID: u8 = 0x16;
const LIST_32_BIT_SERVICE_SOLICITATION_UUIDS: u8 = 0x1f;
const SERVICE_DATA_32_BIT_UUID: u8 = 0x20;
const SERVICE_DATA_128_BIT_UUID: u8 = 0x21;
const TRANSPORT_DISCOVERY_DATA: u8 = 0x26;
const MANUFACTURER_SPECIFIC_DATA: u8 = 0xff;
const SERVICE_AD_TYPES: [u8; 3] = [
    COMPLETE_LIST_16_BIT_SERVICE_UUIDS,
    COMPLETE_LIST_32_BIT_SERVICE_UUIDS,
    COMPLETE_LIST_128_BIT_SERVICE_UUIDS,
];
const SOLICIT_AD_TYPES: [u8; 3] = [
    LIST_16_BIT_SERVICE_SOLICITATION_UUIDS,
    LIST_32_BIT_SERVICE_SOLICITATION_UUIDS,
    LIST_128_BIT_SERVICE_SOLICITATION_UUIDS,
];

const LEGACY_ADV_DATA_LEN_MAX: usize = 31;
const EXT_ADV_DATA_LEN_MAX: usize = 254;

// Invalid advertising set id.
const INVALID_ADV_ID: i32 = 0xff;

// Invalid advertising set id.
pub const INVALID_REG_ID: i32 = -1;

impl Into<bt_topshim::profiles::gatt::AdvertiseParameters> for AdvertisingSetParameters {
    fn into(self) -> bt_topshim::profiles::gatt::AdvertiseParameters {
        let mut props: u16 = 0;
        if self.connectable {
            props |= 0x01;
        }
        if self.scannable {
            props |= 0x02;
        }
        if self.is_legacy {
            props |= 0x10;
        }
        if self.is_anonymous {
            props |= 0x20;
        }
        if self.include_tx_power {
            props |= 0x40;
        }

        let interval = clamp(self.interval, INTERVAL_MIN, INTERVAL_MAX - INTERVAL_DELTA);

        bt_topshim::profiles::gatt::AdvertiseParameters {
            advertising_event_properties: props,
            min_interval: interval as u32,
            max_interval: (interval + INTERVAL_DELTA) as u32,
            channel_map: 0x07 as u8, // all channels
            tx_power: self.tx_power_level as i8,
            primary_advertising_phy: self.primary_phy.into(),
            secondary_advertising_phy: self.secondary_phy.into(),
            scan_request_notification_enable: 0 as u8, // false
            own_address_type: self.own_address_type as i8,
        }
    }
}

impl AdvertiseData {
    fn append_adv_data(dest: &mut Vec<u8>, ad_type: u8, ad_payload: &[u8]) {
        let len = clamp(ad_payload.len(), 0, 254);
        dest.push((len + 1) as u8);
        dest.push(ad_type);
        dest.extend(&ad_payload[..len]);
    }

    fn append_uuids(dest: &mut Vec<u8>, ad_types: &[u8; 3], uuids: &Vec<Uuid>) {
        let mut uuid16_bytes = Vec::<u8>::new();
        let mut uuid32_bytes = Vec::<u8>::new();
        let mut uuid128_bytes = Vec::<u8>::new();

        // For better transmission efficiency, we generate a compact
        // advertisement data by converting UUIDs into shorter binary forms
        // and then group them by their length in order.
        // The data generated for UUIDs looks like:
        // [16-bit_UUID_LIST, 32-bit_UUID_LIST, 128-bit_UUID_LIST].
        for uuid in uuids {
            let uuid_slice = UuidHelper::get_shortest_slice(&uuid.uu);
            let id: Vec<u8> = uuid_slice.iter().rev().cloned().collect();
            match id.len() {
                2 => uuid16_bytes.extend(id),
                4 => uuid32_bytes.extend(id),
                16 => uuid128_bytes.extend(id),
                _ => (),
            }
        }

        let bytes_list = vec![uuid16_bytes, uuid32_bytes, uuid128_bytes];
        for (ad_type, bytes) in
            ad_types.iter().zip(bytes_list.iter()).filter(|(_, bytes)| bytes.len() > 0)
        {
            AdvertiseData::append_adv_data(dest, *ad_type, bytes);
        }
    }

    fn append_service_uuids(dest: &mut Vec<u8>, uuids: &Vec<Uuid>) {
        AdvertiseData::append_uuids(dest, &SERVICE_AD_TYPES, uuids);
    }

    fn append_solicit_uuids(dest: &mut Vec<u8>, uuids: &Vec<Uuid>) {
        AdvertiseData::append_uuids(dest, &SOLICIT_AD_TYPES, uuids);
    }

    fn append_service_data(dest: &mut Vec<u8>, service_data: &HashMap<String, Vec<u8>>) {
        for (uuid, data) in
            service_data.iter().filter_map(|(s, d)| UuidHelper::parse_string(s).map(|s| (s, d)))
        {
            let uuid_slice = UuidHelper::get_shortest_slice(&uuid.uu);
            let concated: Vec<u8> = uuid_slice.iter().rev().chain(data).cloned().collect();
            match uuid_slice.len() {
                2 => AdvertiseData::append_adv_data(dest, SERVICE_DATA_16_BIT_UUID, &concated),
                4 => AdvertiseData::append_adv_data(dest, SERVICE_DATA_32_BIT_UUID, &concated),
                16 => AdvertiseData::append_adv_data(dest, SERVICE_DATA_128_BIT_UUID, &concated),
                _ => (),
            }
        }
    }

    fn append_device_name(dest: &mut Vec<u8>, device_name: &String) {
        if device_name.len() == 0 {
            return;
        }

        let (ad_type, name) = if device_name.len() > DEVICE_NAME_MAX {
            (SHORTENED_LOCAL_NAME, [&device_name.as_bytes()[..DEVICE_NAME_MAX], &[0]].concat())
        } else {
            (COMPLETE_LOCAL_NAME, [device_name.as_bytes(), &[0]].concat())
        };
        AdvertiseData::append_adv_data(dest, ad_type, &name);
    }

    fn append_manufacturer_data(dest: &mut Vec<u8>, manufacturer_data: &HashMap<ManfId, Vec<u8>>) {
        for (m, data) in manufacturer_data.iter().sorted() {
            let concated = [&m.to_le_bytes()[..], data].concat();
            AdvertiseData::append_adv_data(dest, MANUFACTURER_SPECIFIC_DATA, &concated);
        }
    }

    fn append_transport_discovery_data(
        dest: &mut Vec<u8>,
        transport_discovery_data: &Vec<Vec<u8>>,
    ) {
        for tdd in transport_discovery_data.iter().filter(|tdd| tdd.len() > 0) {
            AdvertiseData::append_adv_data(dest, TRANSPORT_DISCOVERY_DATA, &tdd);
        }
    }

    /// Creates raw data from the AdvertiseData.
    pub fn make_with(&self, device_name: &String) -> Vec<u8> {
        let mut bytes = Vec::<u8>::new();
        if self.include_device_name {
            AdvertiseData::append_device_name(&mut bytes, device_name);
        }
        if self.include_tx_power_level {
            // Lower layers will fill tx power level.
            AdvertiseData::append_adv_data(&mut bytes, TX_POWER_LEVEL, &[0]);
        }
        AdvertiseData::append_manufacturer_data(&mut bytes, &self.manufacturer_data);
        AdvertiseData::append_service_uuids(&mut bytes, &self.service_uuids);
        AdvertiseData::append_service_data(&mut bytes, &self.service_data);
        AdvertiseData::append_solicit_uuids(&mut bytes, &self.solicit_uuids);
        AdvertiseData::append_transport_discovery_data(&mut bytes, &self.transport_discovery_data);
        bytes
    }

    /// Validates the raw data as advertisement data.
    pub fn validate_raw_data(is_legacy: bool, bytes: &Vec<u8>) -> bool {
        bytes.len() <= if is_legacy { LEGACY_ADV_DATA_LEN_MAX } else { EXT_ADV_DATA_LEN_MAX }
    }

    /// Checks if the advertisement can be upgraded to extended.
    pub fn can_upgrade(parameters: &mut AdvertisingSetParameters, adv_bytes: &Vec<u8>) -> bool {
        if parameters.is_legacy && !AdvertiseData::validate_raw_data(true, adv_bytes) {
            info!("Auto upgrading advertisement to extended");
            parameters.is_legacy = false;
            return true;
        }

        false
    }
}

impl Into<bt_topshim::profiles::gatt::PeriodicAdvertisingParameters>
    for PeriodicAdvertisingParameters
{
    fn into(self) -> bt_topshim::profiles::gatt::PeriodicAdvertisingParameters {
        let mut p = bt_topshim::profiles::gatt::PeriodicAdvertisingParameters::default();

        let interval = clamp(
            self.interval,
            PERIODIC_INTERVAL_MIN,
            PERIODIC_INTERVAL_MAX - PERIODIC_INTERVAL_DELTA,
        );

        p.enable = true;
        p.include_adi = false;
        p.min_interval = interval as u16;
        p.max_interval = p.min_interval + (PERIODIC_INTERVAL_DELTA as u16);
        if self.include_tx_power {
            p.periodic_advertising_properties |= 0x40;
        }

        p
    }
}

// Keeps information of an advertising set.
#[derive(Debug, PartialEq, Copy, Clone)]
struct AdvertisingSetInfo {
    /// Identifies the advertising set when it's started successfully.
    adv_id: Option<AdvertiserId>,

    /// Identifies callback associated.
    callback_id: CallbackId,

    /// Identifies the advertising set when it's registered.
    reg_id: RegId,

    /// Whether the advertising set has been enabled.
    enabled: bool,

    /// Whether the advertising set has been paused.
    paused: bool,

    /// Whether the stop of advertising set is held.
    /// This flag is set when an advertising set is stopped while we're not able to do it, such as:
    /// - The system is suspending / suspended
    /// - The advertising set is not yet valid (started)
    ///
    /// The advertising set will be stopped on system resumed / advertising set becomes ready.
    stopped: bool,

    /// Advertising duration, in 10 ms unit.
    adv_timeout: u16,

    /// Maximum number of extended advertising events the controller
    /// shall attempt to send before terminating the extended advertising.
    adv_events: u8,

    /// Whether the legacy advertisement will be used.
    legacy: bool,
}

impl AdvertisingSetInfo {
    fn new(
        callback_id: CallbackId,
        adv_timeout: u16,
        adv_events: u8,
        legacy: bool,
        reg_id: RegId,
    ) -> Self {
        AdvertisingSetInfo {
            adv_id: None,
            callback_id,
            reg_id,
            enabled: false,
            paused: false,
            stopped: false,
            adv_timeout,
            adv_events,
            legacy,
        }
    }

    /// Gets advertising set registration ID.
    fn reg_id(&self) -> RegId {
        self.reg_id
    }

    /// Gets associated callback ID.
    fn callback_id(&self) -> CallbackId {
        self.callback_id
    }

    /// Updates advertiser ID.
    fn set_adv_id(&mut self, id: Option<AdvertiserId>) {
        self.adv_id = id;
    }

    /// Gets advertiser ID, which is required for advertising |BleAdvertiserInterface|.
    fn adv_id(&self) -> u8 {
        // As advertiser ID was from topshim originally, type casting is safe.
        self.adv_id.unwrap_or(INVALID_ADV_ID) as u8
    }

    /// Updates advertising set status.
    fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    /// Returns true if the advertising set has been enabled, false otherwise.
    fn is_enabled(&self) -> bool {
        self.enabled
    }

    /// Marks the advertising set as paused or not.
    fn set_paused(&mut self, paused: bool) {
        self.paused = paused;
    }

    /// Returns true if the advertising set has been paused, false otherwise.
    fn is_paused(&self) -> bool {
        self.paused
    }

    /// Marks the advertising set as stopped.
    fn set_stopped(&mut self) {
        self.stopped = true;
    }

    /// Returns true if the advertising set has been stopped, false otherwise.
    fn is_stopped(&self) -> bool {
        self.stopped
    }

    /// Gets adv_timeout.
    fn adv_timeout(&self) -> u16 {
        self.adv_timeout
    }

    /// Gets adv_events.
    fn adv_events(&self) -> u8 {
        self.adv_events
    }

    /// Returns whether the legacy advertisement will be used.
    fn is_legacy(&self) -> bool {
        self.legacy
    }

    /// Returns whether the advertising set is valid.
    fn is_valid(&self) -> bool {
        self.adv_id.is_some()
    }
}

// Manages advertising sets and the callbacks.
pub(crate) struct AdvertiseManager {
    tx: Sender<Message>,
    adv_manager_impl: Option<Box<dyn AdvertiseManagerOps + Send>>,
}

impl AdvertiseManager {
    pub(crate) fn new(tx: Sender<Message>) -> Self {
        AdvertiseManager { tx, adv_manager_impl: None }
    }

    /// Initializes the AdvertiseManager
    /// This needs to be called after Bluetooth is ready because we need to query LE features.
    pub(crate) fn initialize(
        &mut self,
        gatt: Arc<Mutex<Gatt>>,
        adapter: Arc<Mutex<Box<Bluetooth>>>,
        is_le_ext_adv_supported: bool,
    ) {
        self.adv_manager_impl = if is_le_ext_adv_supported {
            info!("AdvertiseManager: Selected extended advertising stack");
            Some(Box::new(AdvertiseManagerImpl::new(self.tx.clone(), gatt, adapter)))
        } else {
            info!("AdvertiseManager: Selected software rotation stack");
            Some(Box::new(SoftwareRotationAdvertiseManagerImpl::new(
                self.tx.clone(),
                gatt,
                adapter,
            )))
        }
    }

    pub fn get_impl(&mut self) -> &mut Box<dyn AdvertiseManagerOps + Send> {
        self.adv_manager_impl.as_mut().unwrap()
    }
}

struct AdvertiseManagerImpl {
    callbacks: Callbacks<dyn IAdvertisingSetCallback + Send>,
    sets: HashMap<RegId, AdvertisingSetInfo>,
    suspend_mode: SuspendMode,
    gatt: Arc<Mutex<Gatt>>,
    adapter: Arc<Mutex<Box<Bluetooth>>>,
}

impl AdvertiseManagerImpl {
    fn new(
        tx: Sender<Message>,
        gatt: Arc<Mutex<Gatt>>,
        adapter: Arc<Mutex<Box<Bluetooth>>>,
    ) -> Self {
        AdvertiseManagerImpl {
            callbacks: Callbacks::new(tx, Message::AdvertiserCallbackDisconnected),
            sets: HashMap::new(),
            suspend_mode: SuspendMode::Normal,
            gatt,
            adapter,
        }
    }

    // Returns the minimum unoccupied register ID from 0.
    fn new_reg_id(&mut self) -> RegId {
        (0..)
            .find(|id| !self.sets.contains_key(id))
            .expect("There must be an unoccupied register ID")
    }

    /// Adds an advertising set.
    fn add(&mut self, s: AdvertisingSetInfo) {
        if let Some(old) = self.sets.insert(s.reg_id(), s) {
            warn!("An advertising set with the same reg_id ({}) exists. Drop it!", old.reg_id);
        }
    }

    /// Returns an iterator of valid advertising sets.
    fn valid_sets(&self) -> impl Iterator<Item = &AdvertisingSetInfo> {
        self.sets.iter().filter_map(|(_, s)| s.adv_id.map(|_| s))
    }

    /// Returns an iterator of enabled advertising sets.
    fn enabled_sets(&self) -> impl Iterator<Item = &AdvertisingSetInfo> {
        self.valid_sets().filter(|s| s.is_enabled())
    }

    /// Returns an iterator of stopped advertising sets.
    fn stopped_sets(&self) -> impl Iterator<Item = &AdvertisingSetInfo> {
        self.valid_sets().filter(|s| s.is_stopped())
    }

    fn find_reg_id(&self, adv_id: AdvertiserId) -> Option<RegId> {
        for (_, s) in &self.sets {
            if s.adv_id == Some(adv_id) {
                return Some(s.reg_id());
            }
        }
        return None;
    }

    /// Returns a mutable reference to the advertising set with the reg_id specified.
    fn get_mut_by_reg_id(&mut self, reg_id: RegId) -> Option<&mut AdvertisingSetInfo> {
        self.sets.get_mut(&reg_id)
    }

    /// Returns a shared reference to the advertising set with the reg_id specified.
    fn get_by_reg_id(&self, reg_id: RegId) -> Option<&AdvertisingSetInfo> {
        self.sets.get(&reg_id)
    }

    /// Returns a mutable reference to the advertising set with the advertiser ID specified.
    fn get_mut_by_advertiser_id(
        &mut self,
        adv_id: AdvertiserId,
    ) -> Option<&mut AdvertisingSetInfo> {
        if let Some(reg_id) = self.find_reg_id(adv_id) {
            return self.get_mut_by_reg_id(reg_id);
        }
        None
    }

    /// Returns a shared reference to the advertising set with the advertiser ID specified.
    fn get_by_advertiser_id(&self, adv_id: AdvertiserId) -> Option<&AdvertisingSetInfo> {
        if let Some(reg_id) = self.find_reg_id(adv_id) {
            return self.get_by_reg_id(reg_id);
        }
        None
    }

    /// Removes the advertising set with the reg_id specified.
    ///
    /// Returns the advertising set if found, None otherwise.
    fn remove_by_reg_id(&mut self, reg_id: RegId) -> Option<AdvertisingSetInfo> {
        self.sets.remove(&reg_id)
    }

    /// Removes the advertising set with the specified advertiser ID.
    ///
    /// Returns the advertising set if found, None otherwise.
    fn remove_by_advertiser_id(&mut self, adv_id: AdvertiserId) -> Option<AdvertisingSetInfo> {
        if let Some(reg_id) = self.find_reg_id(adv_id) {
            return self.remove_by_reg_id(reg_id);
        }
        None
    }

    /// Returns callback of the advertising set.
    fn get_callback(
        &mut self,
        s: &AdvertisingSetInfo,
    ) -> Option<&mut Box<dyn IAdvertisingSetCallback + Send>> {
        self.callbacks.get_by_id_mut(s.callback_id())
    }

    /// Update suspend mode.
    fn set_suspend_mode(&mut self, suspend_mode: SuspendMode) {
        if suspend_mode != self.suspend_mode {
            self.suspend_mode = suspend_mode;
            self.notify_suspend_mode();
        }
    }

    /// Gets current suspend mode.
    fn suspend_mode(&mut self) -> SuspendMode {
        self.suspend_mode.clone()
    }

    /// Notify current suspend mode to all active callbacks.
    fn notify_suspend_mode(&mut self) {
        let suspend_mode = &self.suspend_mode;
        self.callbacks.for_all_callbacks(|callback| {
            callback.on_suspend_mode_change(suspend_mode.clone());
        });
    }

    fn get_adapter_name(&self) -> String {
        self.adapter.lock().unwrap().get_name()
    }
}

pub enum AdvertiserActions {
    /// Triggers the rotation of the advertising set.
    /// Should only be used in the software rotation stack.
    RunRotate,
}

/// Defines all required ops for an AdvertiseManager to communicate with the upper/lower layers.
pub(crate) trait AdvertiseManagerOps:
    IBluetoothAdvertiseManager + BtifGattAdvCallbacks
{
    /// Prepares for suspend
    fn enter_suspend(&mut self);

    /// Undoes previous suspend preparation
    fn exit_suspend(&mut self);

    /// Handles advertise manager actions
    fn handle_action(&mut self, action: AdvertiserActions);
}

impl AdvertiseManagerOps for AdvertiseManagerImpl {
    fn enter_suspend(&mut self) {
        self.set_suspend_mode(SuspendMode::Suspending);

        let mut pausing_cnt = 0;
        for s in self.sets.values_mut().filter(|s| s.is_valid() && s.is_enabled()) {
            s.set_paused(true);
            self.gatt.lock().unwrap().advertiser.enable(
                s.adv_id(),
                false,
                s.adv_timeout(),
                s.adv_events(),
            );
            pausing_cnt += 1;
        }

        if pausing_cnt == 0 {
            self.set_suspend_mode(SuspendMode::Suspended);
        }
    }

    fn exit_suspend(&mut self) {
        for id in self.stopped_sets().map(|s| s.adv_id()).collect::<Vec<_>>() {
            self.gatt.lock().unwrap().advertiser.unregister(id);
            self.remove_by_advertiser_id(id as AdvertiserId);
        }
        for s in self.sets.values_mut().filter(|s| s.is_valid() && s.is_paused()) {
            s.set_paused(false);
            self.gatt.lock().unwrap().advertiser.enable(
                s.adv_id(),
                true,
                s.adv_timeout(),
                s.adv_events(),
            );
        }

        self.set_suspend_mode(SuspendMode::Normal);
    }

    fn handle_action(&mut self, action: AdvertiserActions) {
        match action {
            AdvertiserActions::RunRotate => {
                error!("Unexpected RunRotate call in hardware offloaded stack");
            }
        }
    }
}

pub trait IBluetoothAdvertiseManager {
    /// Registers callback for BLE advertising.
    fn register_callback(&mut self, callback: Box<dyn IAdvertisingSetCallback + Send>) -> u32;

    /// Unregisters callback for BLE advertising.
    fn unregister_callback(&mut self, callback_id: u32) -> bool;

    /// Creates a new BLE advertising set and start advertising.
    ///
    /// Returns the reg_id for the advertising set, which is used in the callback
    /// `on_advertising_set_started` to identify the advertising set started.
    ///
    /// * `parameters` - Advertising set parameters.
    /// * `advertise_data` - Advertisement data to be broadcasted.
    /// * `scan_response` - Scan response.
    /// * `periodic_parameters` - Periodic advertising parameters. If None, periodic advertising
    ///     will not be started.
    /// * `periodic_data` - Periodic advertising data.
    /// * `duration` - Advertising duration, in 10 ms unit. Valid range is from 1 (10 ms) to
    ///     65535 (655.35 sec). 0 means no advertising timeout.
    /// * `max_ext_adv_events` - Maximum number of extended advertising events the controller
    ///     shall attempt to send before terminating the extended advertising, even if the
    ///     duration has not expired. Valid range is from 1 to 255. 0 means event count limitation.
    /// * `callback_id` - Identifies callback registered in register_advertiser_callback.
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
    ) -> i32;

    /// Disposes a BLE advertising set.
    fn stop_advertising_set(&mut self, advertiser_id: i32);

    /// Queries address associated with the advertising set.
    fn get_own_address(&mut self, advertiser_id: i32);

    /// Enables or disables an advertising set.
    fn enable_advertising_set(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        duration: i32,
        max_ext_adv_events: i32,
    );

    /// Updates advertisement data of the advertising set.
    fn set_advertising_data(&mut self, advertiser_id: i32, data: AdvertiseData);

    /// Set the advertisement data of the advertising set.
    fn set_raw_adv_data(&mut self, advertiser_id: i32, data: Vec<u8>);

    /// Updates scan response of the advertising set.
    fn set_scan_response_data(&mut self, advertiser_id: i32, data: AdvertiseData);

    /// Updates advertising parameters of the advertising set.
    ///
    /// It must be called when advertising is not active.
    fn set_advertising_parameters(
        &mut self,
        advertiser_id: i32,
        parameters: AdvertisingSetParameters,
    );

    /// Updates periodic advertising parameters.
    fn set_periodic_advertising_parameters(
        &mut self,
        advertiser_id: i32,
        parameters: PeriodicAdvertisingParameters,
    );

    /// Updates periodic advertisement data.
    ///
    /// It must be called after `set_periodic_advertising_parameters`, or after
    /// advertising was started with periodic advertising data set.
    fn set_periodic_advertising_data(&mut self, advertiser_id: i32, data: AdvertiseData);

    /// Enables or disables periodic advertising.
    fn set_periodic_advertising_enable(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        include_adi: bool,
    );
}

impl IBluetoothAdvertiseManager for AdvertiseManagerImpl {
    fn register_callback(&mut self, callback: Box<dyn IAdvertisingSetCallback + Send>) -> u32 {
        self.callbacks.add_callback(callback)
    }

    fn unregister_callback(&mut self, callback_id: u32) -> bool {
        for s in self.sets.values_mut().filter(|s| s.callback_id() == callback_id) {
            if s.is_valid() {
                self.gatt.lock().unwrap().advertiser.unregister(s.adv_id());
            } else {
                s.set_stopped();
            }
        }
        self.sets.retain(|_, s| s.callback_id() != callback_id || !s.is_valid());

        self.callbacks.remove_callback(callback_id)
    }

    fn start_advertising_set(
        &mut self,
        mut parameters: AdvertisingSetParameters,
        advertise_data: AdvertiseData,
        scan_response: Option<AdvertiseData>,
        periodic_parameters: Option<PeriodicAdvertisingParameters>,
        periodic_data: Option<AdvertiseData>,
        duration: i32,
        max_ext_adv_events: i32,
        callback_id: u32,
    ) -> i32 {
        if self.suspend_mode() != SuspendMode::Normal {
            return INVALID_REG_ID;
        }

        let device_name = self.get_adapter_name();
        let adv_bytes = advertise_data.make_with(&device_name);
        // TODO(b/311417973): Remove this once we have more robust /device/bluetooth APIs to control extended advertising
        let is_legacy =
            parameters.is_legacy && !AdvertiseData::can_upgrade(&mut parameters, &adv_bytes);
        let params = parameters.into();
        if !AdvertiseData::validate_raw_data(is_legacy, &adv_bytes) {
            warn!("Failed to start advertising set with invalid advertise data");
            return INVALID_REG_ID;
        }
        let scan_bytes =
            if let Some(d) = scan_response { d.make_with(&device_name) } else { Vec::<u8>::new() };
        if !AdvertiseData::validate_raw_data(is_legacy, &scan_bytes) {
            warn!("Failed to start advertising set with invalid scan response");
            return INVALID_REG_ID;
        }
        let periodic_params = if let Some(p) = periodic_parameters {
            p.into()
        } else {
            bt_topshim::profiles::gatt::PeriodicAdvertisingParameters::default()
        };
        let periodic_bytes =
            if let Some(d) = periodic_data { d.make_with(&device_name) } else { Vec::<u8>::new() };
        if !AdvertiseData::validate_raw_data(false, &periodic_bytes) {
            warn!("Failed to start advertising set with invalid periodic data");
            return INVALID_REG_ID;
        }
        let adv_timeout = clamp(duration, 0, 0xffff) as u16;
        let adv_events = clamp(max_ext_adv_events, 0, 0xff) as u8;

        let reg_id = self.new_reg_id();
        let s = AdvertisingSetInfo::new(callback_id, adv_timeout, adv_events, is_legacy, reg_id);
        self.add(s);

        self.gatt.lock().unwrap().advertiser.start_advertising_set(
            reg_id,
            params,
            adv_bytes,
            scan_bytes,
            periodic_params,
            periodic_bytes,
            adv_timeout,
            adv_events,
        );
        reg_id
    }

    fn stop_advertising_set(&mut self, advertiser_id: i32) {
        let s = if let Some(s) = self.get_by_advertiser_id(advertiser_id) {
            s.clone()
        } else {
            return;
        };

        if self.suspend_mode() != SuspendMode::Normal {
            if !s.is_stopped() {
                warn!("Deferred advertisement unregistering due to suspending");
                self.get_mut_by_advertiser_id(advertiser_id).unwrap().set_stopped();
                if let Some(cb) = self.get_callback(&s) {
                    cb.on_advertising_set_stopped(advertiser_id);
                }
            }
            return;
        }

        self.gatt.lock().unwrap().advertiser.unregister(s.adv_id());
        if let Some(cb) = self.get_callback(&s) {
            cb.on_advertising_set_stopped(advertiser_id);
        }
        self.remove_by_advertiser_id(advertiser_id);
    }

    fn get_own_address(&mut self, advertiser_id: i32) {
        if self.suspend_mode() != SuspendMode::Normal {
            return;
        }

        if let Some(s) = self.get_by_advertiser_id(advertiser_id) {
            self.gatt.lock().unwrap().advertiser.get_own_address(s.adv_id());
        }
    }

    fn enable_advertising_set(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        duration: i32,
        max_ext_adv_events: i32,
    ) {
        if self.suspend_mode() != SuspendMode::Normal {
            return;
        }

        let adv_timeout = clamp(duration, 0, 0xffff) as u16;
        let adv_events = clamp(max_ext_adv_events, 0, 0xff) as u8;

        if let Some(s) = self.get_by_advertiser_id(advertiser_id) {
            self.gatt.lock().unwrap().advertiser.enable(
                s.adv_id(),
                enable,
                adv_timeout,
                adv_events,
            );
        }
    }

    fn set_advertising_data(&mut self, advertiser_id: i32, data: AdvertiseData) {
        if self.suspend_mode() != SuspendMode::Normal {
            return;
        }

        let device_name = self.get_adapter_name();
        let bytes = data.make_with(&device_name);

        if let Some(s) = self.get_by_advertiser_id(advertiser_id) {
            if !AdvertiseData::validate_raw_data(s.is_legacy(), &bytes) {
                warn!("AdvertiseManagerImpl {}: invalid advertise data to update", advertiser_id);
                return;
            }
            self.gatt.lock().unwrap().advertiser.set_data(s.adv_id(), false, bytes);
        }
    }

    fn set_raw_adv_data(&mut self, advertiser_id: i32, data: Vec<u8>) {
        if self.suspend_mode() != SuspendMode::Normal {
            return;
        }

        if let Some(s) = self.get_by_advertiser_id(advertiser_id) {
            if !AdvertiseData::validate_raw_data(s.is_legacy(), &data) {
                warn!(
                    "AdvertiseManagerImpl {}: invalid raw advertise data to update",
                    advertiser_id
                );
                return;
            }
            self.gatt.lock().unwrap().advertiser.set_data(s.adv_id(), false, data);
        }
    }

    fn set_scan_response_data(&mut self, advertiser_id: i32, data: AdvertiseData) {
        if self.suspend_mode() != SuspendMode::Normal {
            return;
        }

        let device_name = self.get_adapter_name();
        let bytes = data.make_with(&device_name);

        if let Some(s) = self.get_by_advertiser_id(advertiser_id) {
            if !AdvertiseData::validate_raw_data(s.is_legacy(), &bytes) {
                warn!("AdvertiseManagerImpl {}: invalid scan response to update", advertiser_id);
                return;
            }
            self.gatt.lock().unwrap().advertiser.set_data(s.adv_id(), true, bytes);
        }
    }

    fn set_advertising_parameters(
        &mut self,
        advertiser_id: i32,
        parameters: AdvertisingSetParameters,
    ) {
        if self.suspend_mode() != SuspendMode::Normal {
            return;
        }

        let params = parameters.into();

        if let Some(s) = self.get_by_advertiser_id(advertiser_id) {
            let was_enabled = s.is_enabled();
            if was_enabled {
                self.gatt.lock().unwrap().advertiser.enable(
                    s.adv_id(),
                    false,
                    s.adv_timeout(),
                    s.adv_events(),
                );
            }
            self.gatt.lock().unwrap().advertiser.set_parameters(s.adv_id(), params);
            if was_enabled {
                self.gatt.lock().unwrap().advertiser.enable(
                    s.adv_id(),
                    true,
                    s.adv_timeout(),
                    s.adv_events(),
                );
            }
        }
    }

    fn set_periodic_advertising_parameters(
        &mut self,
        advertiser_id: i32,
        parameters: PeriodicAdvertisingParameters,
    ) {
        if self.suspend_mode() != SuspendMode::Normal {
            return;
        }

        let params = parameters.into();

        if let Some(s) = self.get_by_advertiser_id(advertiser_id) {
            self.gatt
                .lock()
                .unwrap()
                .advertiser
                .set_periodic_advertising_parameters(s.adv_id(), params);
        }
    }

    fn set_periodic_advertising_data(&mut self, advertiser_id: i32, data: AdvertiseData) {
        if self.suspend_mode() != SuspendMode::Normal {
            return;
        }

        let device_name = self.get_adapter_name();
        let bytes = data.make_with(&device_name);

        if let Some(s) = self.get_by_advertiser_id(advertiser_id) {
            if !AdvertiseData::validate_raw_data(false, &bytes) {
                warn!("AdvertiseManagerImpl {}: invalid periodic data to update", advertiser_id);
                return;
            }
            self.gatt.lock().unwrap().advertiser.set_periodic_advertising_data(s.adv_id(), bytes);
        }
    }

    fn set_periodic_advertising_enable(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        include_adi: bool,
    ) {
        if self.suspend_mode() != SuspendMode::Normal {
            return;
        }
        if let Some(s) = self.get_by_advertiser_id(advertiser_id) {
            self.gatt.lock().unwrap().advertiser.set_periodic_advertising_enable(
                s.adv_id(),
                enable,
                include_adi,
            );
        }
    }
}

#[btif_callbacks_dispatcher(dispatch_le_adv_callbacks, GattAdvCallbacks)]
pub(crate) trait BtifGattAdvCallbacks {
    #[btif_callback(OnAdvertisingSetStarted)]
    fn on_advertising_set_started(
        &mut self,
        reg_id: i32,
        advertiser_id: u8,
        tx_power: i8,
        status: AdvertisingStatus,
    );

    #[btif_callback(OnAdvertisingEnabled)]
    fn on_advertising_enabled(&mut self, adv_id: u8, enabled: bool, status: AdvertisingStatus);

    #[btif_callback(OnAdvertisingDataSet)]
    fn on_advertising_data_set(&mut self, adv_id: u8, status: AdvertisingStatus);

    #[btif_callback(OnScanResponseDataSet)]
    fn on_scan_response_data_set(&mut self, adv_id: u8, status: AdvertisingStatus);

    #[btif_callback(OnAdvertisingParametersUpdated)]
    fn on_advertising_parameters_updated(
        &mut self,
        adv_id: u8,
        tx_power: i8,
        status: AdvertisingStatus,
    );

    #[btif_callback(OnPeriodicAdvertisingParametersUpdated)]
    fn on_periodic_advertising_parameters_updated(&mut self, adv_id: u8, status: AdvertisingStatus);

    #[btif_callback(OnPeriodicAdvertisingDataSet)]
    fn on_periodic_advertising_data_set(&mut self, adv_id: u8, status: AdvertisingStatus);

    #[btif_callback(OnPeriodicAdvertisingEnabled)]
    fn on_periodic_advertising_enabled(
        &mut self,
        adv_id: u8,
        enabled: bool,
        status: AdvertisingStatus,
    );

    #[btif_callback(OnOwnAddressRead)]
    fn on_own_address_read(&mut self, adv_id: u8, addr_type: u8, address: RawAddress);
}

impl BtifGattAdvCallbacks for AdvertiseManagerImpl {
    fn on_advertising_set_started(
        &mut self,
        reg_id: i32,
        advertiser_id: u8,
        tx_power: i8,
        status: AdvertisingStatus,
    ) {
        debug!(
            "on_advertising_set_started(): reg_id = {}, advertiser_id = {}, tx_power = {}, status = {:?}",
            reg_id, advertiser_id, tx_power, status
        );

        let s = if let Some(s) = self.sets.get_mut(&reg_id) {
            s
        } else {
            error!("AdvertisingSetInfo not found");
            // An unknown advertising set has started. Unregister it anyway.
            self.gatt.lock().unwrap().advertiser.unregister(advertiser_id);
            return;
        };

        if s.is_stopped() {
            // The advertising set needs to be stopped. This could happen when |unregister_callback|
            // is called before an advertising becomes ready.
            self.gatt.lock().unwrap().advertiser.unregister(advertiser_id);
            self.sets.remove(&reg_id);
            return;
        }

        s.set_adv_id(Some(advertiser_id.into()));
        s.set_enabled(status == AdvertisingStatus::Success);

        if let Some(cb) = self.callbacks.get_by_id_mut(s.callback_id()) {
            cb.on_advertising_set_started(reg_id, advertiser_id.into(), tx_power.into(), status);
        }

        if status != AdvertisingStatus::Success {
            warn!(
                "on_advertising_set_started(): failed! reg_id = {}, status = {:?}",
                reg_id, status
            );
            self.sets.remove(&reg_id);
        }
    }

    fn on_advertising_enabled(&mut self, adv_id: u8, enabled: bool, status: AdvertisingStatus) {
        debug!(
            "on_advertising_enabled(): adv_id = {}, enabled = {}, status = {:?}",
            adv_id, enabled, status
        );

        let advertiser_id: i32 = adv_id.into();

        if let Some(s) = self.get_mut_by_advertiser_id(advertiser_id) {
            s.set_enabled(enabled);
        } else {
            return;
        }

        let s = self.get_by_advertiser_id(advertiser_id).unwrap().clone();
        if let Some(cb) = self.get_callback(&s) {
            cb.on_advertising_enabled(advertiser_id, enabled, status);
        }

        if self.suspend_mode() == SuspendMode::Suspending {
            if self.enabled_sets().count() == 0 {
                self.set_suspend_mode(SuspendMode::Suspended);
            }
        }
    }

    fn on_advertising_data_set(&mut self, adv_id: u8, status: AdvertisingStatus) {
        debug!("on_advertising_data_set(): adv_id = {}, status = {:?}", adv_id, status);

        let advertiser_id: i32 = adv_id.into();
        if None == self.get_by_advertiser_id(advertiser_id) {
            return;
        }
        let s = self.get_by_advertiser_id(advertiser_id).unwrap().clone();

        if let Some(cb) = self.get_callback(&s) {
            cb.on_advertising_data_set(advertiser_id, status);
        }
    }

    fn on_scan_response_data_set(&mut self, adv_id: u8, status: AdvertisingStatus) {
        debug!("on_scan_response_data_set(): adv_id = {}, status = {:?}", adv_id, status);

        let advertiser_id: i32 = adv_id.into();
        if None == self.get_by_advertiser_id(advertiser_id) {
            return;
        }
        let s = self.get_by_advertiser_id(advertiser_id).unwrap().clone();

        if let Some(cb) = self.get_callback(&s) {
            cb.on_scan_response_data_set(advertiser_id, status);
        }
    }

    fn on_advertising_parameters_updated(
        &mut self,
        adv_id: u8,
        tx_power: i8,
        status: AdvertisingStatus,
    ) {
        debug!(
            "on_advertising_parameters_updated(): adv_id = {}, tx_power = {}, status = {:?}",
            adv_id, tx_power, status
        );

        let advertiser_id: i32 = adv_id.into();
        if None == self.get_by_advertiser_id(advertiser_id) {
            return;
        }
        let s = self.get_by_advertiser_id(advertiser_id).unwrap().clone();

        if let Some(cb) = self.get_callback(&s) {
            cb.on_advertising_parameters_updated(advertiser_id, tx_power.into(), status);
        }
    }

    fn on_periodic_advertising_parameters_updated(
        &mut self,
        adv_id: u8,
        status: AdvertisingStatus,
    ) {
        debug!(
            "on_periodic_advertising_parameters_updated(): adv_id = {}, status = {:?}",
            adv_id, status
        );

        let advertiser_id: i32 = adv_id.into();
        if None == self.get_by_advertiser_id(advertiser_id) {
            return;
        }
        let s = self.get_by_advertiser_id(advertiser_id).unwrap().clone();

        if let Some(cb) = self.get_callback(&s) {
            cb.on_periodic_advertising_parameters_updated(advertiser_id, status);
        }
    }

    fn on_periodic_advertising_data_set(&mut self, adv_id: u8, status: AdvertisingStatus) {
        debug!("on_periodic_advertising_data_set(): adv_id = {}, status = {:?}", adv_id, status);

        let advertiser_id: i32 = adv_id.into();
        if None == self.get_by_advertiser_id(advertiser_id) {
            return;
        }
        let s = self.get_by_advertiser_id(advertiser_id).unwrap().clone();

        if let Some(cb) = self.get_callback(&s) {
            cb.on_periodic_advertising_data_set(advertiser_id, status);
        }
    }

    fn on_periodic_advertising_enabled(
        &mut self,
        adv_id: u8,
        enabled: bool,
        status: AdvertisingStatus,
    ) {
        debug!(
            "on_periodic_advertising_enabled(): adv_id = {}, enabled = {}, status = {:?}",
            adv_id, enabled, status
        );

        let advertiser_id: i32 = adv_id.into();
        if None == self.get_by_advertiser_id(advertiser_id) {
            return;
        }
        let s = self.get_by_advertiser_id(advertiser_id).unwrap().clone();

        if let Some(cb) = self.get_callback(&s) {
            cb.on_periodic_advertising_enabled(advertiser_id, enabled, status);
        }
    }

    fn on_own_address_read(&mut self, adv_id: u8, addr_type: u8, address: RawAddress) {
        debug!(
            "on_own_address_read(): adv_id = {}, addr_type = {}, address = {:?}",
            adv_id, addr_type, address
        );

        let advertiser_id: i32 = adv_id.into();
        if None == self.get_by_advertiser_id(advertiser_id) {
            return;
        }
        let s = self.get_by_advertiser_id(advertiser_id).unwrap().clone();

        if let Some(cb) = self.get_callback(&s) {
            cb.on_own_address_read(advertiser_id, addr_type.into(), address.to_string());
        }
    }
}

/// The underlying legacy advertising rotates every SOFTWARE_ROTATION_INTERVAL seconds.
const SOFTWARE_ROTATION_INTERVAL: Duration = Duration::from_secs(2);

/// The ID of a software rotation advertising.
///
/// From DBus API's perspective this is used as both Advertiser ID and Register ID.
/// Unlike the extended advertising stack we can't propagate the LibBluetooth Advertiser ID to
/// DBus clients because there can be at most 1 advertiser in LibBluetooth layer at the same time.
pub type SoftwareRotationAdvertierId = i32;

struct SoftwareRotationAdvertiseInfo {
    id: SoftwareRotationAdvertierId,
    callback_id: u32,

    advertising_params: AdvertisingSetParameters,
    advertising_data: Vec<u8>,
    scan_response_data: Vec<u8>,

    /// Filled in on the first time the advertiser started.
    tx_power: Option<i32>,

    /// True if it's advertising (from DBus client's perspective), false otherwise.
    enabled: bool,
    duration: i32,
    /// None means no timeout
    expire_time: Option<Instant>,
}

enum SoftwareRotationAdvertiseState {
    /// No advertiser is running in LibBluetooth.
    Stopped,
    /// A StartAdvertisingSet call to LibBluetooth is pending.
    Pending(SoftwareRotationAdvertierId),
    /// An advertiser is running in LibBluetooth, i.e., an OnAdvertisingSetStarted is received.
    /// Parameters: ID, LibBluetooth BLE Advertiser ID, rotation timer handle
    Advertising(SoftwareRotationAdvertierId, u8, JoinHandle<()>),
}

struct SoftwareRotationAdvertiseManagerImpl {
    callbacks: Callbacks<dyn IAdvertisingSetCallback + Send>,
    suspend_mode: SuspendMode,
    gatt: Arc<Mutex<Gatt>>,
    adapter: Arc<Mutex<Box<Bluetooth>>>,
    tx: Sender<Message>,

    state: SoftwareRotationAdvertiseState,
    adv_info: HashMap<SoftwareRotationAdvertierId, SoftwareRotationAdvertiseInfo>,
    /// The enabled advertising sets to be rotate.
    /// When they are removed from the queue, OnAdvertisingEnabled needs to be sent.
    /// Note that the current advertiser running in LibBluetooth must *NOT* be in the queue.
    adv_queue: VecDeque<SoftwareRotationAdvertierId>,
}

impl SoftwareRotationAdvertiseManagerImpl {
    fn new(
        tx: Sender<Message>,
        gatt: Arc<Mutex<Gatt>>,
        adapter: Arc<Mutex<Box<Bluetooth>>>,
    ) -> Self {
        Self {
            callbacks: Callbacks::new(tx.clone(), Message::AdvertiserCallbackDisconnected),
            suspend_mode: SuspendMode::Normal,
            gatt,
            adapter,
            tx,
            state: SoftwareRotationAdvertiseState::Stopped,
            adv_info: HashMap::new(),
            adv_queue: VecDeque::new(),
        }
    }
}

impl SoftwareRotationAdvertiseManagerImpl {
    /// Updates suspend mode.
    fn set_suspend_mode(&mut self, suspend_mode: SuspendMode) {
        if suspend_mode != self.suspend_mode {
            self.suspend_mode = suspend_mode.clone();
            self.callbacks.for_all_callbacks(|cb| {
                cb.on_suspend_mode_change(suspend_mode.clone());
            });
        }
    }

    fn get_adapter_name(&self) -> String {
        self.adapter.lock().unwrap().get_name()
    }

    /// Returns the ID of the advertiser running in LibBluetooth.
    fn current_id(&self) -> Option<SoftwareRotationAdvertierId> {
        match &self.state {
            SoftwareRotationAdvertiseState::Pending(id) => Some(*id),
            SoftwareRotationAdvertiseState::Advertising(id, _, _) => Some(*id),
            SoftwareRotationAdvertiseState::Stopped => None,
        }
    }

    /// Returns the minimum unoccupied ID from 0.
    fn new_id(&mut self) -> SoftwareRotationAdvertierId {
        // The advertiser running in LibBluetooth may have been removed in this layer.
        // Avoid conflicting with it.
        let current_id = self.current_id();
        (0..)
            .find(|id| !self.adv_info.contains_key(id) && Some(*id) != current_id)
            .expect("There must be an unoccupied register ID")
    }

    fn is_pending(&self) -> bool {
        matches!(&self.state, SoftwareRotationAdvertiseState::Pending(_))
    }

    fn is_stopped(&self) -> bool {
        matches!(&self.state, SoftwareRotationAdvertiseState::Stopped)
    }

    /// Clears the removed or disabled advertisers from the queue and invokes callback.
    fn refresh_queue(&mut self) {
        let now = Instant::now();
        let adv_info = &mut self.adv_info;
        let callbacks = &mut self.callbacks;
        self.adv_queue.retain(|id| {
            let Some(info) = adv_info.get_mut(id) else {
                // This advertiser has been removed.
                return false;
            };
            if info.expire_time.map_or(false, |t| t < now) {
                // This advertiser has expired.
                info.enabled = false;
                callbacks.get_by_id_mut(info.callback_id).map(|cb| {
                    cb.on_advertising_enabled(info.id, false, AdvertisingStatus::Success);
                });
            }
            info.enabled
        });
    }

    fn stop_current_advertising(&mut self) {
        match &self.state {
            SoftwareRotationAdvertiseState::Advertising(id, adv_id, handle) => {
                handle.abort();
                self.gatt.lock().unwrap().advertiser.unregister(*adv_id);
                self.adv_queue.push_back(*id);
                self.state = SoftwareRotationAdvertiseState::Stopped;
            }
            SoftwareRotationAdvertiseState::Pending(_) => {
                error!("stop_current_advertising: Unexpected Pending state");
            }
            SoftwareRotationAdvertiseState::Stopped => {}
        };
    }

    fn start_next_advertising(&mut self) {
        match &self.state {
            SoftwareRotationAdvertiseState::Stopped => {
                self.state = loop {
                    let Some(id) = self.adv_queue.pop_front() else {
                        break SoftwareRotationAdvertiseState::Stopped;
                    };
                    let Some(info) = self.adv_info.get(&id) else {
                        error!("start_next_advertising: Unknown ID, which means queue is not refreshed!");
                        continue;
                    };
                    self.gatt.lock().unwrap().advertiser.start_advertising_set(
                        id,
                        info.advertising_params.clone().into(),
                        info.advertising_data.clone(),
                        info.scan_response_data.clone(),
                        Default::default(), // Unsupported periodic_parameters
                        vec![],             // Unsupported periodic_data
                        0,                  // Set no timeout. Timeout is controlled in this layer.
                        0,                  // Unsupported max_ext_adv_events
                    );
                    break SoftwareRotationAdvertiseState::Pending(id);
                }
            }
            SoftwareRotationAdvertiseState::Pending(_) => {
                error!("start_next_advertising: Unexpected Pending state");
            }
            SoftwareRotationAdvertiseState::Advertising(_, _, _) => {
                error!("start_next_advertising: Unexpected Advertising state");
            }
        };
    }

    fn run_rotate(&mut self) {
        if self.is_pending() {
            return;
        }
        let Some(current_id) = self.current_id() else {
            // State is Stopped. Try to start next one.
            self.refresh_queue();
            self.start_next_advertising();
            return;
        };
        // We are Advertising. Checks if the current advertiser is still allowed
        // to advertise, or if we should schedule the next one in the queue.
        let current_is_enabled = {
            let now = Instant::now();
            if let Some(info) = self.adv_info.get(&current_id) {
                if info.enabled {
                    info.expire_time.map_or(true, |t| t >= now)
                } else {
                    false
                }
            } else {
                false
            }
        };
        if !current_is_enabled {
            // If current advertiser is not allowed to advertise,
            // stop it and then let |refresh_queue| handle the callback.
            self.stop_current_advertising();
            self.refresh_queue();
            self.start_next_advertising();
        } else {
            // Current advertiser is still enabled, refresh the other advertisers in the queue.
            self.refresh_queue();
            if self.adv_queue.is_empty() {
                // No need to rotate.
            } else {
                self.stop_current_advertising();
                self.start_next_advertising();
            }
        }
    }
}

impl AdvertiseManagerOps for SoftwareRotationAdvertiseManagerImpl {
    fn enter_suspend(&mut self) {
        if self.suspend_mode != SuspendMode::Normal {
            return;
        }

        self.set_suspend_mode(SuspendMode::Suspending);
        if self.is_pending() {
            // We will unregister it on_advertising_set_started and then set mode to suspended.
            return;
        }
        self.stop_current_advertising();
        self.set_suspend_mode(SuspendMode::Suspended);
    }

    fn exit_suspend(&mut self) {
        if self.suspend_mode != SuspendMode::Suspended {
            return;
        }
        self.refresh_queue();
        self.start_next_advertising();
        self.set_suspend_mode(SuspendMode::Normal);
    }

    fn handle_action(&mut self, action: AdvertiserActions) {
        match action {
            AdvertiserActions::RunRotate => {
                if self.suspend_mode == SuspendMode::Normal {
                    self.run_rotate();
                }
            }
        }
    }
}

/// Generates expire time from now per the definition in IBluetoothAdvertiseManager
///
/// None means never timeout.
fn gen_expire_time_from_now(duration: i32) -> Option<Instant> {
    let duration = clamp(duration, 0, 0xffff) as u64;
    if duration != 0 {
        Some(Instant::now() + Duration::from_millis(duration * 10))
    } else {
        None
    }
}

impl IBluetoothAdvertiseManager for SoftwareRotationAdvertiseManagerImpl {
    fn register_callback(&mut self, callback: Box<dyn IAdvertisingSetCallback + Send>) -> u32 {
        self.callbacks.add_callback(callback)
    }

    fn unregister_callback(&mut self, callback_id: u32) -> bool {
        self.adv_info.retain(|_, info| info.callback_id != callback_id);
        let ret = self.callbacks.remove_callback(callback_id);
        if let Some(current_id) = self.current_id() {
            if !self.adv_info.contains_key(&current_id) {
                self.run_rotate();
            }
        }
        ret
    }

    fn start_advertising_set(
        &mut self,
        advertising_params: AdvertisingSetParameters,
        advertising_data: AdvertiseData,
        scan_response_data: Option<AdvertiseData>,
        periodic_parameters: Option<PeriodicAdvertisingParameters>,
        periodic_data: Option<AdvertiseData>,
        duration: i32,
        max_ext_adv_events: i32,
        callback_id: u32,
    ) -> i32 {
        if self.suspend_mode != SuspendMode::Normal {
            return INVALID_REG_ID;
        }

        let is_legacy = advertising_params.is_legacy;
        let device_name = self.get_adapter_name();

        let advertising_data = advertising_data.make_with(&device_name);
        if !AdvertiseData::validate_raw_data(is_legacy, &advertising_data) {
            warn!("Failed to start advertising set with invalid advertising data");
            return INVALID_REG_ID;
        }

        let scan_response_data =
            scan_response_data.map_or(vec![], |data| data.make_with(&device_name));
        if !AdvertiseData::validate_raw_data(is_legacy, &scan_response_data) {
            warn!("Failed to start advertising set with invalid scan response data");
            return INVALID_REG_ID;
        }

        if periodic_parameters.is_some() {
            warn!("Periodic parameters is not supported in software rotation stack, ignored");
        }
        if periodic_data.is_some() {
            warn!("Periodic data is not supported in software rotation stack, ignored");
        }
        if max_ext_adv_events != 0 {
            warn!("max_ext_adv_events is not supported in software rotation stack, ignored");
        }

        let id = self.new_id();

        // expire_time will be determined on this advertiser is started at the first time.
        self.adv_info.insert(
            id,
            SoftwareRotationAdvertiseInfo {
                id,
                callback_id,
                advertising_params,
                advertising_data,
                scan_response_data,
                tx_power: None,
                enabled: true,
                duration,
                expire_time: None,
            },
        );
        // Schedule it as the next one and rotate.
        self.adv_queue.push_front(id);
        self.run_rotate();

        id
    }

    fn stop_advertising_set(&mut self, adv_id: i32) {
        let current_id = self.current_id();
        let Some(info) = self.adv_info.remove(&adv_id) else {
            warn!("stop_advertising_set: Unknown adv_id {}", adv_id);
            return;
        };
        self.callbacks.get_by_id_mut(info.callback_id).map(|cb| {
            cb.on_advertising_set_stopped(info.id);
        });
        if current_id == Some(info.id) {
            self.run_rotate();
        }
    }

    fn get_own_address(&mut self, _adv_id: i32) {
        error!("get_own_address is not supported in software rotation stack");
    }

    fn enable_advertising_set(
        &mut self,
        adv_id: i32,
        enable: bool,
        duration: i32,
        max_ext_adv_events: i32,
    ) {
        if self.suspend_mode != SuspendMode::Normal {
            return;
        }

        let current_id = self.current_id();
        let Some(info) = self.adv_info.get_mut(&adv_id) else {
            warn!("enable_advertising_set: Unknown adv_id {}", adv_id);
            return;
        };

        if max_ext_adv_events != 0 {
            warn!("max_ext_adv_events is not supported in software rotation stack, ignored");
        }

        info.enabled = enable;
        // We won't really call enable() to LibBluetooth so calculate the expire time right now.
        info.expire_time = gen_expire_time_from_now(duration);
        // This actually won't be used as the expire_time is already determined.
        // Still fill it in to keep the data updated.
        info.duration = duration;

        if enable && !self.adv_queue.contains(&info.id) && current_id != Some(info.id) {
            // The adv was not enabled and not in the queue. Invoke callback and queue it.
            self.callbacks.get_by_id_mut(info.callback_id).map(|cb| {
                cb.on_advertising_enabled(info.id, false, AdvertisingStatus::Success);
            });
            self.adv_queue.push_back(info.id);
            if self.is_stopped() {
                self.start_next_advertising();
            }
        } else if !enable && current_id == Some(info.id) {
            self.run_rotate();
        }
    }

    fn set_advertising_data(&mut self, adv_id: i32, data: AdvertiseData) {
        if self.suspend_mode != SuspendMode::Normal {
            return;
        }

        let current_id = self.current_id();
        let device_name = self.get_adapter_name();
        let Some(info) = self.adv_info.get_mut(&adv_id) else {
            warn!("set_advertising_data: Unknown adv_id {}", adv_id);
            return;
        };
        let data = data.make_with(&device_name);
        if !AdvertiseData::validate_raw_data(info.advertising_params.is_legacy, &data) {
            warn!("set_advertising_data {}: invalid advertise data to update", adv_id);
            return;
        }
        info.advertising_data = data;
        self.callbacks.get_by_id_mut(info.callback_id).map(|cb| {
            cb.on_advertising_data_set(info.id, AdvertisingStatus::Success);
        });

        if current_id == Some(info.id) {
            self.run_rotate();
        }
    }

    fn set_raw_adv_data(&mut self, adv_id: i32, data: Vec<u8>) {
        if self.suspend_mode != SuspendMode::Normal {
            return;
        }

        let current_id = self.current_id();
        let Some(info) = self.adv_info.get_mut(&adv_id) else {
            warn!("set_raw_adv_data: Unknown adv_id {}", adv_id);
            return;
        };
        if !AdvertiseData::validate_raw_data(info.advertising_params.is_legacy, &data) {
            warn!("set_raw_adv_data {}: invalid raw advertise data to update", adv_id);
            return;
        }
        info.advertising_data = data;
        self.callbacks.get_by_id_mut(info.callback_id).map(|cb| {
            cb.on_advertising_data_set(info.id, AdvertisingStatus::Success);
        });

        if current_id == Some(info.id) {
            self.run_rotate();
        }
    }

    fn set_scan_response_data(&mut self, adv_id: i32, data: AdvertiseData) {
        if self.suspend_mode != SuspendMode::Normal {
            return;
        }

        let current_id = self.current_id();
        let device_name = self.get_adapter_name();
        let Some(info) = self.adv_info.get_mut(&adv_id) else {
            warn!("set_scan_response_data: Unknown adv_id {}", adv_id);
            return;
        };
        let data = data.make_with(&device_name);
        if !AdvertiseData::validate_raw_data(info.advertising_params.is_legacy, &data) {
            warn!("set_scan_response_data {}: invalid scan response to update", adv_id);
            return;
        }
        info.scan_response_data = data;
        self.callbacks.get_by_id_mut(info.callback_id).map(|cb| {
            cb.on_scan_response_data_set(info.id, AdvertisingStatus::Success);
        });

        if current_id == Some(info.id) {
            self.run_rotate();
        }
    }

    fn set_advertising_parameters(&mut self, adv_id: i32, params: AdvertisingSetParameters) {
        if self.suspend_mode != SuspendMode::Normal {
            return;
        }

        let current_id = self.current_id();
        let Some(info) = self.adv_info.get_mut(&adv_id) else {
            warn!("set_advertising_parameters: Unknown adv_id {}", adv_id);
            return;
        };
        info.advertising_params = params;
        let Some(tx_power) = info.tx_power else {
            error!("set_advertising_parameters: tx_power is None! Is this called before adv has started?");
            return;
        };
        self.callbacks.get_by_id_mut(info.callback_id).map(|cb| {
            cb.on_advertising_parameters_updated(info.id, tx_power, AdvertisingStatus::Success);
        });

        if current_id == Some(info.id) {
            self.run_rotate();
        }
    }

    fn set_periodic_advertising_parameters(
        &mut self,
        _adv_id: i32,
        _parameters: PeriodicAdvertisingParameters,
    ) {
        error!("set_periodic_advertising_parameters is not supported in software rotation stack");
    }

    fn set_periodic_advertising_data(&mut self, _adv_id: i32, _data: AdvertiseData) {
        error!("set_periodic_advertising_data is not supported in software rotation stack");
    }

    fn set_periodic_advertising_enable(&mut self, _adv_id: i32, _enable: bool, _include_adi: bool) {
        error!("set_periodic_advertising_enable is not supported in software rotation stack");
    }
}

impl BtifGattAdvCallbacks for SoftwareRotationAdvertiseManagerImpl {
    fn on_advertising_set_started(
        &mut self,
        reg_id: i32,
        adv_id: u8,
        tx_power: i8,
        status: AdvertisingStatus,
    ) {
        debug!(
            "on_advertising_set_started(): reg_id = {}, advertiser_id = {}, tx_power = {}, status = {:?}",
            reg_id, adv_id, tx_power, status
        );

        // Unregister if it's unexpected.
        match &self.state {
            SoftwareRotationAdvertiseState::Pending(pending_id) if pending_id == &reg_id => {}
            _ => {
                error!(
                    "Unexpected on_advertising_set_started reg_id = {}, adv_id = {}, status = {:?}",
                    reg_id, adv_id, status
                );
                if status == AdvertisingStatus::Success {
                    self.gatt.lock().unwrap().advertiser.unregister(adv_id);
                }
                return;
            }
        }
        // Switch out from the pending state.
        self.state = if status != AdvertisingStatus::Success {
            warn!("on_advertising_set_started failed: reg_id = {}, status = {:?}", reg_id, status);
            SoftwareRotationAdvertiseState::Stopped
        } else {
            let txl = self.tx.clone();
            SoftwareRotationAdvertiseState::Advertising(
                reg_id,
                adv_id,
                tokio::spawn(async move {
                    loop {
                        time::sleep(SOFTWARE_ROTATION_INTERVAL).await;
                        let _ = txl
                            .send(Message::AdvertiserActions(AdvertiserActions::RunRotate))
                            .await;
                    }
                }),
            )
        };

        // 1. Handle on_advertising_set_started callback if it's the first time it started
        // 2. Stop advertising if it's removed or disabled
        // 3. Disable or remove the advertiser if it failed
        if let Some(info) = self.adv_info.get_mut(&reg_id) {
            if info.tx_power.is_none() {
                // tx_power is none means it's the first time this advertiser started.
                if status != AdvertisingStatus::Success {
                    self.callbacks.get_by_id_mut(info.callback_id).map(|cb| {
                        cb.on_advertising_set_started(info.id, INVALID_ADV_ID, 0, status);
                    });
                    self.adv_info.remove(&reg_id);
                } else {
                    info.tx_power = Some(tx_power.into());
                    info.expire_time = gen_expire_time_from_now(info.duration);
                    self.callbacks.get_by_id_mut(info.callback_id).map(|cb| {
                        cb.on_advertising_set_started(info.id, info.id, tx_power.into(), status);
                    });
                }
            } else {
                // Not the first time. This means we are not able to report the failure through
                // on_advertising_set_started if it failed. Disable it instead in that case.
                if status != AdvertisingStatus::Success {
                    info.enabled = false;
                    // Push to the queue and let refresh_queue handle the disabled callback.
                    self.adv_queue.push_back(reg_id);
                } else {
                    if !info.enabled {
                        self.stop_current_advertising();
                    }
                }
            }
        } else {
            self.stop_current_advertising();
        }

        // Rotate again if the next advertiser is new. We need to consume all
        // "first time" advertiser before suspended to make sure callbacks are sent.
        if let Some(id) = self.adv_queue.front() {
            if let Some(info) = self.adv_info.get(id) {
                if info.tx_power.is_none() {
                    self.run_rotate();
                    return;
                }
            }
        }

        // We're fine to suspend since there is no advertiser pending callback.
        if self.suspend_mode != SuspendMode::Normal {
            self.stop_current_advertising();
            self.set_suspend_mode(SuspendMode::Suspended);
            return;
        }

        // If the current advertiser is stopped for some reason, schedule the next one.
        if self.is_stopped() {
            self.refresh_queue();
            self.start_next_advertising();
        }
    }

    fn on_advertising_enabled(&mut self, _adv_id: u8, _enabled: bool, _status: AdvertisingStatus) {
        error!("Unexpected on_advertising_enabled in software rotation stack");
    }

    fn on_advertising_data_set(&mut self, _adv_id: u8, _status: AdvertisingStatus) {
        error!("Unexpected on_advertising_data_set in software rotation stack");
    }

    fn on_scan_response_data_set(&mut self, _adv_id: u8, _status: AdvertisingStatus) {
        error!("Unexpected on_scan_response_data_set in software rotation stack");
    }

    fn on_advertising_parameters_updated(
        &mut self,
        _adv_id: u8,
        _tx_power: i8,
        _status: AdvertisingStatus,
    ) {
        error!("Unexpected on_advertising_parameters_updated in software rotation stack");
    }

    fn on_periodic_advertising_parameters_updated(
        &mut self,
        _adv_id: u8,
        _status: AdvertisingStatus,
    ) {
        error!("Unexpected on_periodic_advertising_parameters_updated in software rotation stack");
    }

    fn on_periodic_advertising_data_set(&mut self, _adv_id: u8, _status: AdvertisingStatus) {
        error!("Unexpected on_periodic_advertising_data_set in software rotation stack");
    }

    fn on_periodic_advertising_enabled(
        &mut self,
        _adv_id: u8,
        _enabled: bool,
        _status: AdvertisingStatus,
    ) {
        error!("Unexpected on_periodic_advertising_enabled in software rotation stack");
    }

    fn on_own_address_read(&mut self, _adv_id: u8, _addr_type: u8, _address: RawAddress) {
        error!("Unexpected on_own_address_read in software rotation stack");
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::iter::FromIterator;

    #[test]
    fn test_append_ad_data_clamped() {
        let mut bytes = Vec::<u8>::new();
        let mut ans = Vec::<u8>::new();
        ans.push(255);
        ans.push(102);
        ans.extend(Vec::<u8>::from_iter(0..254));

        let payload = Vec::<u8>::from_iter(0..255);
        AdvertiseData::append_adv_data(&mut bytes, 102, &payload);
        assert_eq!(bytes, ans);
    }

    #[test]
    fn test_append_ad_data_multiple() {
        let mut bytes = Vec::<u8>::new();

        let payload = vec![0 as u8, 1, 2, 3, 4];
        AdvertiseData::append_adv_data(&mut bytes, 100, &payload);
        AdvertiseData::append_adv_data(&mut bytes, 101, &[0]);
        assert_eq!(bytes, vec![6 as u8, 100, 0, 1, 2, 3, 4, 2, 101, 0]);
    }

    #[test]
    fn test_append_service_uuids() {
        let mut bytes = Vec::<u8>::new();
        let uuid_16 =
            Uuid::from(UuidHelper::from_string("0000fef3-0000-1000-8000-00805f9b34fb").unwrap());
        let uuids = vec![uuid_16.clone()];
        let exp_16: Vec<u8> = vec![3, 0x3, 0xf3, 0xfe];
        AdvertiseData::append_service_uuids(&mut bytes, &uuids);
        assert_eq!(bytes, exp_16);

        let mut bytes = Vec::<u8>::new();
        let uuid_32 =
            Uuid::from(UuidHelper::from_string("00112233-0000-1000-8000-00805f9b34fb").unwrap());
        let uuids = vec![uuid_32.clone()];
        let exp_32: Vec<u8> = vec![5, 0x5, 0x33, 0x22, 0x11, 0x0];
        AdvertiseData::append_service_uuids(&mut bytes, &uuids);
        assert_eq!(bytes, exp_32);

        let mut bytes = Vec::<u8>::new();
        let uuid_128 =
            Uuid::from(UuidHelper::from_string("00010203-0405-0607-0809-0a0b0c0d0e0f").unwrap());
        let uuids = vec![uuid_128.clone()];
        let exp_128: Vec<u8> = vec![
            17, 0x7, 0xf, 0xe, 0xd, 0xc, 0xb, 0xa, 0x9, 0x8, 0x7, 0x6, 0x5, 0x4, 0x3, 0x2, 0x1, 0x0,
        ];
        AdvertiseData::append_service_uuids(&mut bytes, &uuids);
        assert_eq!(bytes, exp_128);

        let mut bytes = Vec::<u8>::new();
        let uuids = vec![uuid_16, uuid_32, uuid_128];
        let exp_bytes: Vec<u8> =
            [exp_16.as_slice(), exp_32.as_slice(), exp_128.as_slice()].concat();
        AdvertiseData::append_service_uuids(&mut bytes, &uuids);
        assert_eq!(bytes, exp_bytes);

        // Interleaved UUIDs.
        let mut bytes = Vec::<u8>::new();
        let uuid_16_2 =
            Uuid::from(UuidHelper::from_string("0000aabb-0000-1000-8000-00805f9b34fb").unwrap());
        let uuids = vec![uuid_16, uuid_128, uuid_16_2, uuid_32];
        let exp_16: Vec<u8> = vec![5, 0x3, 0xf3, 0xfe, 0xbb, 0xaa];
        let exp_bytes: Vec<u8> =
            [exp_16.as_slice(), exp_32.as_slice(), exp_128.as_slice()].concat();
        AdvertiseData::append_service_uuids(&mut bytes, &uuids);
        assert_eq!(bytes, exp_bytes);
    }

    #[test]
    fn test_append_solicit_uuids() {
        let mut bytes = Vec::<u8>::new();
        let uuid_16 =
            Uuid::from(UuidHelper::from_string("0000fef3-0000-1000-8000-00805f9b34fb").unwrap());
        let uuid_32 =
            Uuid::from(UuidHelper::from_string("00112233-0000-1000-8000-00805f9b34fb").unwrap());
        let uuid_128 =
            Uuid::from(UuidHelper::from_string("00010203-0405-0607-0809-0a0b0c0d0e0f").unwrap());
        let uuids = vec![uuid_16, uuid_32, uuid_128];
        let exp_16: Vec<u8> = vec![3, 0x14, 0xf3, 0xfe];
        let exp_32: Vec<u8> = vec![5, 0x1f, 0x33, 0x22, 0x11, 0x0];
        let exp_128: Vec<u8> = vec![
            17, 0x15, 0xf, 0xe, 0xd, 0xc, 0xb, 0xa, 0x9, 0x8, 0x7, 0x6, 0x5, 0x4, 0x3, 0x2, 0x1,
            0x0,
        ];
        let exp_bytes: Vec<u8> =
            [exp_16.as_slice(), exp_32.as_slice(), exp_128.as_slice()].concat();
        AdvertiseData::append_solicit_uuids(&mut bytes, &uuids);
        assert_eq!(bytes, exp_bytes);
    }

    #[test]
    fn test_append_service_data_good_id() {
        let mut bytes = Vec::<u8>::new();
        let uuid_str = "0000fef3-0000-1000-8000-00805f9b34fb".to_string();
        let mut service_data = HashMap::new();
        let data: Vec<u8> = vec![
            0x4A, 0x17, 0x23, 0x41, 0x39, 0x37, 0x45, 0x11, 0x16, 0x60, 0x1D, 0xB8, 0x27, 0xA2,
            0xEF, 0xAA, 0xFE, 0x58, 0x04, 0x9F, 0xE3, 0x8F, 0xD0, 0x04, 0x29, 0x4F, 0xC2,
        ];
        service_data.insert(uuid_str, data.clone());
        let mut exp_bytes: Vec<u8> = vec![30, 0x16, 0xf3, 0xfe];
        exp_bytes.extend(data);
        AdvertiseData::append_service_data(&mut bytes, &service_data);
        assert_eq!(bytes, exp_bytes);
    }

    #[test]
    fn test_append_service_data_bad_id() {
        let mut bytes = Vec::<u8>::new();
        let uuid_str = "fef3".to_string();
        let mut service_data = HashMap::new();
        let data: Vec<u8> = vec![
            0x4A, 0x17, 0x23, 0x41, 0x39, 0x37, 0x45, 0x11, 0x16, 0x60, 0x1D, 0xB8, 0x27, 0xA2,
            0xEF, 0xAA, 0xFE, 0x58, 0x04, 0x9F, 0xE3, 0x8F, 0xD0, 0x04, 0x29, 0x4F, 0xC2,
        ];
        service_data.insert(uuid_str, data.clone());
        let exp_bytes: Vec<u8> = Vec::new();
        AdvertiseData::append_service_data(&mut bytes, &service_data);
        assert_eq!(bytes, exp_bytes);
    }

    #[test]
    fn test_append_device_name() {
        let mut bytes = Vec::<u8>::new();
        let complete_name = "abc".to_string();
        let exp_bytes: Vec<u8> = vec![5, 0x9, 0x61, 0x62, 0x63, 0x0];
        AdvertiseData::append_device_name(&mut bytes, &complete_name);
        assert_eq!(bytes, exp_bytes);

        let mut bytes = Vec::<u8>::new();
        let shortened_name = "abcdefghijklmnopqrstuvwxyz7890".to_string();
        let exp_bytes: Vec<u8> = vec![
            28, 0x8, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d,
            0x6e, 0x6f, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x0,
        ];
        AdvertiseData::append_device_name(&mut bytes, &shortened_name);
        assert_eq!(bytes, exp_bytes);
    }

    #[test]
    fn test_append_manufacturer_data() {
        let mut bytes = Vec::<u8>::new();
        let manufacturer_data = HashMap::from([(0x0123 as u16, vec![0, 1, 2])]);
        let exp_bytes: Vec<u8> = vec![6, 0xff, 0x23, 0x01, 0x0, 0x1, 0x2];
        AdvertiseData::append_manufacturer_data(&mut bytes, &manufacturer_data);
        assert_eq!(bytes, exp_bytes);
    }

    #[test]
    fn test_append_transport_discovery_data() {
        let mut bytes = Vec::<u8>::new();
        let transport_discovery_data = vec![vec![0, 1, 2]];
        let exp_bytes: Vec<u8> = vec![0x4, 0x26, 0x0, 0x1, 0x2];
        AdvertiseData::append_transport_discovery_data(&mut bytes, &transport_discovery_data);
        assert_eq!(bytes, exp_bytes);

        let mut bytes = Vec::<u8>::new();
        let transport_discovery_data = vec![vec![1, 2, 4, 8], vec![0xa, 0xb]];
        let exp_bytes: Vec<u8> = vec![0x5, 0x26, 0x1, 0x2, 0x4, 0x8, 3, 0x26, 0xa, 0xb];
        AdvertiseData::append_transport_discovery_data(&mut bytes, &transport_discovery_data);
        assert_eq!(bytes, exp_bytes);
    }
}
