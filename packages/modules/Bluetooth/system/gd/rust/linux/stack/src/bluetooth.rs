//! Anything related to the adapter API (IBluetooth).

use bt_topshim::btif::{
    BaseCallbacks, BaseCallbacksDispatcher, BluetoothInterface, BluetoothProperty, BtAclState,
    BtAddrType, BtBondState, BtConnectionDirection, BtConnectionState, BtDeviceType, BtDiscMode,
    BtDiscoveryState, BtHciErrorCode, BtPinCode, BtPropertyType, BtScanMode, BtSspVariant, BtState,
    BtStatus, BtThreadEvent, BtTransport, BtVendorProductInfo, DisplayAddress, RawAddress,
    ToggleableProfile, Uuid, Uuid128Bit, INVALID_RSSI,
};
use bt_topshim::{
    controller, metrics,
    profiles::gatt::GattStatus,
    profiles::hfp::EscoCodingFormat,
    profiles::hid_host::{
        BthhConnectionState, BthhHidInfo, BthhProtocolMode, BthhReportType, BthhStatus,
        HHCallbacks, HHCallbacksDispatcher, HidHost,
    },
    profiles::sdp::{BtSdpRecord, Sdp, SdpCallbacks, SdpCallbacksDispatcher},
    profiles::ProfileConnectionState,
    topstack,
};

use bt_utils::array_utils;
use bt_utils::cod::{is_cod_hid_combo, is_cod_hid_keyboard};
use bt_utils::uhid::{UHid, BD_ADDR_DEFAULT};
use btif_macros::{btif_callback, btif_callbacks_dispatcher};

use log::{debug, warn};
use num_derive::{FromPrimitive, ToPrimitive};
use num_traits::cast::ToPrimitive;
use num_traits::pow;
use std::collections::{HashMap, HashSet};
use std::convert::TryInto;
use std::fs::File;
use std::hash::Hash;
use std::io::Write;
use std::process;
use std::sync::{Arc, Condvar, Mutex};
use std::time::Duration;
use std::time::Instant;
use tokio::sync::mpsc::Sender;
use tokio::task::JoinHandle;
use tokio::time;

use crate::battery_service::BatteryServiceActions;
use crate::bluetooth_admin::{BluetoothAdmin, IBluetoothAdmin};
use crate::bluetooth_gatt::{
    BluetoothGatt, GattActions, IBluetoothGatt, IScannerCallback, ScanResult,
};
use crate::bluetooth_media::{BluetoothMedia, IBluetoothMedia, MediaActions};
use crate::callbacks::Callbacks;
use crate::socket_manager::SocketActions;
use crate::uuid::{Profile, UuidHelper, HOGP};
use crate::{APIMessage, BluetoothAPI, Message, RPCProxy, SuspendMode};

pub(crate) const FLOSS_VER: u16 = 0x0001;
const DEFAULT_DISCOVERY_TIMEOUT_MS: u64 = 12800;
const MIN_ADV_INSTANCES_FOR_MULTI_ADV: u8 = 5;

/// Devices that were last seen longer than this duration are considered stale
/// if they haven't already bonded or connected. Once this duration expires, the
/// clear event should be sent to clients.
const FOUND_DEVICE_FRESHNESS: Duration = Duration::from_secs(30);

/// This is the value returned from Bluetooth Interface calls.
// TODO(241930383): Add enum to topshim
const BTM_SUCCESS: i32 = 0;

const PID_DIR: &str = "/var/run/bluetooth";

/// Represents various roles the adapter supports.
#[derive(Debug, FromPrimitive, ToPrimitive)]
#[repr(u32)]
pub enum BtAdapterRole {
    Central = 0,
    Peripheral,
    CentralPeripheral,
}
/// Defines the adapter API.
pub trait IBluetooth {
    /// Adds a callback from a client who wishes to observe adapter events.
    fn register_callback(&mut self, callback: Box<dyn IBluetoothCallback + Send>) -> u32;

    /// Removes registered callback.
    fn unregister_callback(&mut self, callback_id: u32) -> bool;

    /// Adds a callback from a client who wishes to observe connection events.
    fn register_connection_callback(
        &mut self,
        callback: Box<dyn IBluetoothConnectionCallback + Send>,
    ) -> u32;

    /// Removes registered callback.
    fn unregister_connection_callback(&mut self, callback_id: u32) -> bool;

    /// Inits the bluetooth interface. Should always be called before enable.
    fn init(&mut self, init_flags: Vec<String>) -> bool;

    /// Enables the adapter.
    ///
    /// Returns true if the request is accepted.
    fn enable(&mut self) -> bool;

    /// Disables the adapter.
    ///
    /// Returns true if the request is accepted.
    fn disable(&mut self) -> bool;

    /// Cleans up the bluetooth interface. Should always be called after disable.
    fn cleanup(&mut self);

    /// Returns the Bluetooth address of the local adapter.
    fn get_address(&self) -> String;

    /// Gets supported UUIDs by the local adapter.
    fn get_uuids(&self) -> Vec<Uuid128Bit>;

    /// Gets the local adapter name.
    fn get_name(&self) -> String;

    /// Sets the local adapter name.
    fn set_name(&self, name: String) -> bool;

    /// Gets the bluetooth class.
    fn get_bluetooth_class(&self) -> u32;

    /// Sets the bluetooth class.
    fn set_bluetooth_class(&self, cod: u32) -> bool;

    /// Returns whether the adapter is discoverable.
    fn get_discoverable(&self) -> bool;

    /// Returns the adapter discoverable timeout.
    fn get_discoverable_timeout(&self) -> u32;

    /// Sets discoverability. If discoverable, limits the duration with given value.
    fn set_discoverable(&mut self, mode: BtDiscMode, duration: u32) -> bool;

    /// Returns whether multi-advertisement is supported.
    /// A minimum number of 5 advertising instances is required for multi-advertisment support.
    fn is_multi_advertisement_supported(&self) -> bool;

    /// Returns whether LE extended advertising is supported.
    fn is_le_extended_advertising_supported(&self) -> bool;

    /// Starts BREDR Inquiry.
    fn start_discovery(&mut self) -> bool;

    /// Cancels BREDR Inquiry.
    fn cancel_discovery(&mut self) -> bool;

    /// Checks if discovery is started.
    fn is_discovering(&self) -> bool;

    /// Checks when discovery ends in milliseconds from now.
    fn get_discovery_end_millis(&self) -> u64;

    /// Initiates pairing to a remote device. Triggers connection if not already started.
    fn create_bond(&mut self, device: BluetoothDevice, transport: BtTransport) -> bool;

    /// Cancels any pending bond attempt on given device.
    fn cancel_bond_process(&mut self, device: BluetoothDevice) -> bool;

    /// Removes pairing for given device.
    fn remove_bond(&mut self, device: BluetoothDevice) -> bool;

    /// Returns a list of known bonded devices.
    fn get_bonded_devices(&self) -> Vec<BluetoothDevice>;

    /// Gets the bond state of a single device.
    fn get_bond_state(&self, device: BluetoothDevice) -> BtBondState;

    /// Set pin on bonding device.
    fn set_pin(&self, device: BluetoothDevice, accept: bool, pin_code: Vec<u8>) -> bool;

    /// Set passkey on bonding device.
    fn set_passkey(&self, device: BluetoothDevice, accept: bool, passkey: Vec<u8>) -> bool;

    /// Confirm that a pairing should be completed on a bonding device.
    fn set_pairing_confirmation(&self, device: BluetoothDevice, accept: bool) -> bool;

    /// Gets the name of the remote device.
    fn get_remote_name(&self, device: BluetoothDevice) -> String;

    /// Gets the type of the remote device.
    fn get_remote_type(&self, device: BluetoothDevice) -> BtDeviceType;

    /// Gets the alias of the remote device.
    fn get_remote_alias(&self, device: BluetoothDevice) -> String;

    /// Sets the alias of the remote device.
    fn set_remote_alias(&mut self, device: BluetoothDevice, new_alias: String);

    /// Gets the class of the remote device.
    fn get_remote_class(&self, device: BluetoothDevice) -> u32;

    /// Gets the appearance of the remote device.
    fn get_remote_appearance(&self, device: BluetoothDevice) -> u16;

    /// Gets whether the remote device is connected.
    fn get_remote_connected(&self, device: BluetoothDevice) -> bool;

    /// Gets whether the remote device can wake the system.
    fn get_remote_wake_allowed(&self, device: BluetoothDevice) -> bool;

    /// Gets the vendor and product information of the remote device.
    fn get_remote_vendor_product_info(&self, device: BluetoothDevice) -> BtVendorProductInfo;

    /// Get the address type of the remote device.
    fn get_remote_address_type(&self, device: BluetoothDevice) -> BtAddrType;

    /// Get the RSSI of the remote device.
    fn get_remote_rssi(&self, device: BluetoothDevice) -> i8;

    /// Returns a list of connected devices.
    fn get_connected_devices(&self) -> Vec<BluetoothDevice>;

    /// Gets the connection state of a single device.
    fn get_connection_state(&self, device: BluetoothDevice) -> BtConnectionState;

    /// Gets the connection state of a specific profile.
    fn get_profile_connection_state(&self, profile: Uuid128Bit) -> ProfileConnectionState;

    /// Returns the cached UUIDs of a remote device.
    fn get_remote_uuids(&self, device: BluetoothDevice) -> Vec<Uuid128Bit>;

    /// Triggers SDP to get UUIDs of a remote device.
    fn fetch_remote_uuids(&self, device: BluetoothDevice) -> bool;

    /// Triggers SDP and searches for a specific UUID on a remote device.
    fn sdp_search(&self, device: BluetoothDevice, uuid: Uuid128Bit) -> bool;

    /// Creates a new SDP record.
    fn create_sdp_record(&mut self, sdp_record: BtSdpRecord) -> bool;

    /// Removes the SDP record associated with the provided handle.
    fn remove_sdp_record(&self, handle: i32) -> bool;

    /// Connect all profiles supported by device and enabled on adapter.
    fn connect_all_enabled_profiles(&mut self, device: BluetoothDevice) -> bool;

    /// Disconnect all profiles supported by device and enabled on adapter.
    /// Note that it includes all custom profiles enabled by the users e.g. through SocketManager or
    /// BluetoothGatt interfaces; The device shall be disconnected on baseband eventually.
    fn disconnect_all_enabled_profiles(&mut self, device: BluetoothDevice) -> bool;

    /// Returns whether WBS is supported.
    fn is_wbs_supported(&self) -> bool;

    /// Returns whether SWB is supported.
    fn is_swb_supported(&self) -> bool;

    /// Returns a list of all the roles that are supported.
    fn get_supported_roles(&self) -> Vec<BtAdapterRole>;

    /// Returns whether the coding format is supported.
    fn is_coding_format_supported(&self, coding_format: EscoCodingFormat) -> bool;
}

/// Adapter API for Bluetooth qualification and verification.
///
/// This interface is provided for testing and debugging.
/// Clients should not use this interface for production.
pub trait IBluetoothQALegacy {
    /// Returns whether the adapter is connectable.
    fn get_connectable(&self) -> bool;

    /// Sets connectability. Returns true on success, false otherwise.
    fn set_connectable(&mut self, mode: bool) -> bool;

    /// Returns the adapter's Bluetooth friendly name.
    fn get_alias(&self) -> String;

    /// Returns the adapter's Device ID information in modalias format
    /// used by the kernel and udev.
    fn get_modalias(&self) -> String;

    /// Gets HID report on the peer.
    fn get_hid_report(
        &mut self,
        addr: String,
        report_type: BthhReportType,
        report_id: u8,
    ) -> BtStatus;

    /// Sets HID report to the peer.
    fn set_hid_report(
        &mut self,
        addr: String,
        report_type: BthhReportType,
        report: String,
    ) -> BtStatus;

    /// Snd HID data report to the peer.
    fn send_hid_data(&mut self, addr: String, data: String) -> BtStatus;
}

/// Delayed actions from adapter events.
pub enum DelayedActions {
    /// Check whether the current set of found devices are still fresh.
    DeviceFreshnessCheck,

    /// Connect to all supported profiles on target device.
    ConnectAllProfiles(BluetoothDevice),

    /// Scanner for BLE discovery is registered with given status and scanner id.
    BleDiscoveryScannerRegistered(Uuid128Bit, u8, GattStatus),

    /// Scanner for BLE discovery is reporting a result.
    BleDiscoveryScannerResult(ScanResult),
}

/// Serializable device used in various apis.
#[derive(Clone, Debug, Default, PartialEq, Eq, Hash)]
pub struct BluetoothDevice {
    pub address: String,
    pub name: String,
}

impl BluetoothDevice {
    pub(crate) fn new(address: String, name: String) -> BluetoothDevice {
        BluetoothDevice { address, name }
    }

    pub(crate) fn from_properties(in_properties: &Vec<BluetoothProperty>) -> BluetoothDevice {
        let mut address = String::from("");
        let mut name = String::from("");

        for prop in in_properties {
            match &prop {
                BluetoothProperty::BdAddr(bdaddr) => {
                    address = bdaddr.to_string();
                }
                BluetoothProperty::BdName(bdname) => {
                    name = bdname.clone();
                }
                _ => {}
            }
        }

        BluetoothDevice::new(address, name)
    }
}

/// Internal data structure that keeps a map of cached properties for a remote device.
struct BluetoothDeviceContext {
    /// Transport type reported by ACL connection (if completed).
    pub acl_reported_transport: BtTransport,

    pub acl_state: BtAclState,
    pub bond_state: BtBondState,
    pub info: BluetoothDevice,
    pub last_seen: Instant,
    pub properties: HashMap<BtPropertyType, BluetoothProperty>,

    /// Keep track of whether services have been resolved.
    pub services_resolved: bool,

    /// If supported UUIDs weren't available in EIR, wait for services to be
    /// resolved to connect.
    pub wait_to_connect: bool,
}

impl BluetoothDeviceContext {
    pub(crate) fn new(
        bond_state: BtBondState,
        acl_state: BtAclState,
        info: BluetoothDevice,
        last_seen: Instant,
        properties: Vec<BluetoothProperty>,
    ) -> BluetoothDeviceContext {
        let mut device = BluetoothDeviceContext {
            acl_reported_transport: BtTransport::Auto,
            acl_state,
            bond_state,
            info,
            last_seen,
            properties: HashMap::new(),
            services_resolved: false,
            wait_to_connect: false,
        };
        device.update_properties(&properties);
        device
    }

    pub(crate) fn update_properties(&mut self, in_properties: &Vec<BluetoothProperty>) {
        for prop in in_properties {
            // Handle merging of certain properties.
            match &prop {
                BluetoothProperty::BdAddr(bdaddr) => {
                    self.info.address = bdaddr.to_string();
                    self.properties.insert(prop.get_type(), prop.clone());
                }
                BluetoothProperty::BdName(bdname) => {
                    if !bdname.is_empty() {
                        self.info.name = bdname.clone();
                        self.properties.insert(prop.get_type(), prop.clone());
                    }
                }
                _ => {
                    self.properties.insert(prop.get_type(), prop.clone());
                }
            }
        }
    }

    /// Mark this device as seen.
    pub(crate) fn seen(&mut self) {
        self.last_seen = Instant::now();
    }
}

/// Structure to track all the signals for SIGTERM.
pub struct SigData {
    pub enabled: Mutex<bool>,
    pub enabled_notify: Condvar,

    pub thread_attached: Mutex<bool>,
    pub thread_notify: Condvar,
}

/// The interface for adapter callbacks registered through `IBluetooth::register_callback`.
pub trait IBluetoothCallback: RPCProxy {
    /// When any adapter property changes.
    fn on_adapter_property_changed(&mut self, prop: BtPropertyType);

    /// When any device properties change.
    fn on_device_properties_changed(
        &mut self,
        remote_device: BluetoothDevice,
        props: Vec<BtPropertyType>,
    );

    /// When any of the adapter local address is changed.
    fn on_address_changed(&mut self, addr: String);

    /// When the adapter name is changed.
    fn on_name_changed(&mut self, name: String);

    /// When the adapter's discoverable mode is changed.
    fn on_discoverable_changed(&mut self, discoverable: bool);

    /// When a device is found via discovery.
    fn on_device_found(&mut self, remote_device: BluetoothDevice);

    /// When a device is cleared from discovered devices cache.
    fn on_device_cleared(&mut self, remote_device: BluetoothDevice);

    /// When the discovery state is changed.
    fn on_discovering_changed(&mut self, discovering: bool);

    /// When there is a pairing/bonding process and requires agent to display the event to UI.
    fn on_ssp_request(
        &mut self,
        remote_device: BluetoothDevice,
        cod: u32,
        variant: BtSspVariant,
        passkey: u32,
    );

    /// When there is a pin request to display the event to client.
    fn on_pin_request(&mut self, remote_device: BluetoothDevice, cod: u32, min_16_digit: bool);

    /// When there is a auto-gen pin to display the event to client.
    fn on_pin_display(&mut self, remote_device: BluetoothDevice, pincode: String);

    /// When a bonding attempt has completed.
    fn on_bond_state_changed(&mut self, status: u32, device_address: String, state: u32);

    /// When an SDP search has completed.
    fn on_sdp_search_complete(
        &mut self,
        remote_device: BluetoothDevice,
        searched_uuid: Uuid128Bit,
        sdp_records: Vec<BtSdpRecord>,
    );

    /// When an SDP record has been successfully created.
    fn on_sdp_record_created(&mut self, record: BtSdpRecord, handle: i32);
}

/// An interface for other modules to track found remote devices.
pub trait IBluetoothDeviceCallback {
    /// When a device is found via discovery.
    fn on_device_found(&mut self, remote_device: BluetoothDevice);

    /// When a device is cleared from discovered devices cache.
    fn on_device_cleared(&mut self, remote_device: BluetoothDevice);

    /// When a device property is changed.
    fn on_remote_device_properties_changed(
        &mut self,
        remote_device: BluetoothDevice,
        properties: Vec<BluetoothProperty>,
    );
}

pub trait IBluetoothConnectionCallback: RPCProxy {
    /// Notification sent when a remote device completes HCI connection.
    fn on_device_connected(&mut self, remote_device: BluetoothDevice);

    /// Notification sent when a remote device completes HCI disconnection.
    fn on_device_disconnected(&mut self, remote_device: BluetoothDevice);
}

/// Implementation of the adapter API.
pub struct Bluetooth {
    intf: Arc<Mutex<BluetoothInterface>>,

    virt_index: i32,
    hci_index: i32,
    bonded_devices: HashMap<String, BluetoothDeviceContext>,
    ble_scanner_id: Option<u8>,
    ble_scanner_uuid: Option<Uuid128Bit>,
    bluetooth_admin: Arc<Mutex<Box<BluetoothAdmin>>>,
    bluetooth_gatt: Arc<Mutex<Box<BluetoothGatt>>>,
    bluetooth_media: Arc<Mutex<Box<BluetoothMedia>>>,
    callbacks: Callbacks<dyn IBluetoothCallback + Send>,
    connection_callbacks: Callbacks<dyn IBluetoothConnectionCallback + Send>,
    discovering_started: Instant,
    hh: Option<HidHost>,
    is_connectable: bool,
    is_discovering: bool,
    is_discovering_before_suspend: bool,
    is_discovery_paused: bool,
    discovery_suspend_mode: SuspendMode,
    local_address: Option<RawAddress>,
    pending_discovery: bool,
    properties: HashMap<BtPropertyType, BluetoothProperty>,
    profiles_ready: bool,
    found_devices: HashMap<String, BluetoothDeviceContext>,
    freshness_check: Option<JoinHandle<()>>,
    sdp: Option<Sdp>,
    state: BtState,
    tx: Sender<Message>,
    api_tx: Sender<APIMessage>,
    // Internal API members
    discoverable_timeout: Option<JoinHandle<()>>,
    cancelling_devices: HashSet<RawAddress>,
    active_pairing_address: Option<RawAddress>,
    le_supported_states: u64,

    /// Used to notify signal handler that we have turned off the stack.
    sig_notifier: Arc<SigData>,

    /// Virtual uhid device created to keep bluetooth as a wakeup source.
    uhid_wakeup_source: UHid,
}

impl Bluetooth {
    /// Constructs the IBluetooth implementation.
    pub fn new(
        virt_index: i32,
        hci_index: i32,
        tx: Sender<Message>,
        api_tx: Sender<APIMessage>,
        sig_notifier: Arc<SigData>,
        intf: Arc<Mutex<BluetoothInterface>>,
        bluetooth_admin: Arc<Mutex<Box<BluetoothAdmin>>>,
        bluetooth_gatt: Arc<Mutex<Box<BluetoothGatt>>>,
        bluetooth_media: Arc<Mutex<Box<BluetoothMedia>>>,
    ) -> Bluetooth {
        Bluetooth {
            virt_index,
            hci_index,
            bonded_devices: HashMap::new(),
            callbacks: Callbacks::new(tx.clone(), Message::AdapterCallbackDisconnected),
            connection_callbacks: Callbacks::new(
                tx.clone(),
                Message::ConnectionCallbackDisconnected,
            ),
            hh: None,
            ble_scanner_id: None,
            ble_scanner_uuid: None,
            bluetooth_admin,
            bluetooth_gatt,
            bluetooth_media,
            discovering_started: Instant::now(),
            intf,
            is_connectable: false,
            is_discovering: false,
            is_discovering_before_suspend: false,
            is_discovery_paused: false,
            discovery_suspend_mode: SuspendMode::Normal,
            local_address: None,
            pending_discovery: false,
            properties: HashMap::new(),
            profiles_ready: false,
            found_devices: HashMap::new(),
            freshness_check: None,
            sdp: None,
            state: BtState::Off,
            tx,
            api_tx,
            // Internal API members
            discoverable_timeout: None,
            cancelling_devices: HashSet::new(),
            active_pairing_address: None,
            le_supported_states: 0u64,
            sig_notifier,
            uhid_wakeup_source: UHid::new(),
        }
    }

    fn disable_profile(&mut self, profile: &Profile) {
        if !UuidHelper::is_profile_supported(profile) {
            return;
        }

        match profile {
            Profile::Hid => {
                self.hh.as_mut().unwrap().activate_hidp(false);
            }

            Profile::Hogp => {
                self.hh.as_mut().unwrap().activate_hogp(false);
            }

            Profile::A2dpSource | Profile::Hfp | Profile::AvrcpTarget => {
                self.bluetooth_media.lock().unwrap().disable_profile(profile);
            }
            // Ignore profiles that we don't connect.
            _ => (),
        }
    }

    fn enable_profile(&mut self, profile: &Profile) {
        if !UuidHelper::is_profile_supported(profile) {
            return;
        }

        match profile {
            Profile::Hid => {
                self.hh.as_mut().unwrap().activate_hidp(true);
            }

            Profile::Hogp => {
                self.hh.as_mut().unwrap().activate_hogp(true);
            }

            Profile::A2dpSource | Profile::Hfp | Profile::AvrcpTarget => {
                self.bluetooth_media.lock().unwrap().enable_profile(profile);
            }
            // Ignore profiles that we don't connect.
            _ => (),
        }
    }

    fn is_profile_enabled(&self, profile: &Profile) -> Option<bool> {
        if !UuidHelper::is_profile_supported(profile) {
            return None;
        }

        match profile {
            Profile::Hid => Some(self.hh.as_ref().unwrap().is_hidp_activated),

            Profile::Hogp => Some(self.hh.as_ref().unwrap().is_hogp_activated),

            Profile::A2dpSource | Profile::Hfp | Profile::AvrcpTarget => {
                self.bluetooth_media.lock().unwrap().is_profile_enabled(profile)
            }
            // Ignore profiles that we don't connect.
            _ => None,
        }
    }

    pub(crate) fn get_hci_index(&self) -> u16 {
        self.hci_index as u16
    }

    pub fn toggle_enabled_profiles(&mut self, allowed_services: &Vec<Uuid128Bit>) {
        for profile in UuidHelper::get_ordered_supported_profiles().clone() {
            // Only toggle initializable profiles.
            if let Some(enabled) = self.is_profile_enabled(&profile) {
                let allowed = allowed_services.len() == 0
                    || allowed_services.contains(&UuidHelper::get_profile_uuid(&profile).unwrap());

                if allowed && !enabled {
                    debug!("Enabling profile {}", &profile);
                    self.enable_profile(&profile);
                } else if !allowed && enabled {
                    debug!("Disabling profile {}", &profile);
                    self.disable_profile(&profile);
                }
            }
        }

        if self.hh.as_mut().unwrap().configure_enabled_profiles() {
            self.hh.as_mut().unwrap().disable();
            let txl = self.tx.clone();

            tokio::spawn(async move {
                // Wait 100 milliseconds to prevent race condition caused by quick disable then
                // enable.
                // TODO: (b/272191117): don't enable until we're sure disable is done.
                tokio::time::sleep(Duration::from_millis(100)).await;
                let _ = txl.send(Message::HidHostEnable).await;
            });
        }
    }

    pub fn enable_hidhost(&mut self) {
        self.hh.as_mut().unwrap().enable();
    }

    pub fn init_profiles(&mut self) {
        self.bluetooth_gatt.lock().unwrap().enable(true);

        let sdptx = self.tx.clone();
        self.sdp = Some(Sdp::new(&self.intf.lock().unwrap()));
        self.sdp.as_mut().unwrap().initialize(SdpCallbacksDispatcher {
            dispatch: Box::new(move |cb| {
                let txl = sdptx.clone();
                topstack::get_runtime().spawn(async move {
                    let _ = txl.send(Message::Sdp(cb)).await;
                });
            }),
        });

        let hhtx = self.tx.clone();
        self.hh = Some(HidHost::new(&self.intf.lock().unwrap()));
        self.hh.as_mut().unwrap().initialize(HHCallbacksDispatcher {
            dispatch: Box::new(move |cb| {
                let txl = hhtx.clone();
                topstack::get_runtime().spawn(async move {
                    let _ = txl.send(Message::HidHost(cb)).await;
                });
            }),
        });

        let allowed_profiles = self.bluetooth_admin.lock().unwrap().get_allowed_services();
        self.toggle_enabled_profiles(&allowed_profiles);
        // Mark profiles as ready
        self.profiles_ready = true;
    }

    fn update_local_address(&mut self, addr: &RawAddress) {
        self.local_address = Some(addr.clone());

        self.callbacks.for_all_callbacks(|callback| {
            callback.on_address_changed(addr.to_string());
        });
    }

    pub(crate) fn adapter_callback_disconnected(&mut self, id: u32) {
        self.callbacks.remove_callback(id);
    }

    pub(crate) fn connection_callback_disconnected(&mut self, id: u32) {
        self.connection_callbacks.remove_callback(id);
    }

    fn get_remote_device_if_found(&self, address: &str) -> Option<&BluetoothDeviceContext> {
        self.bonded_devices.get(address).or_else(|| self.found_devices.get(address))
    }

    fn get_remote_device_if_found_mut(
        &mut self,
        address: &str,
    ) -> Option<&mut BluetoothDeviceContext> {
        match self.bonded_devices.get_mut(address) {
            None => self.found_devices.get_mut(address),
            some => some,
        }
    }

    fn get_remote_device_info_if_found(&self, remote_address: &str) -> Option<BluetoothDevice> {
        self.get_remote_device_if_found(remote_address)
            .map(|device_context| device_context.info.clone())
    }

    fn get_remote_device_property(
        &self,
        device: &BluetoothDevice,
        property_type: &BtPropertyType,
    ) -> Option<BluetoothProperty> {
        self.get_remote_device_if_found(&device.address)
            .and_then(|d| d.properties.get(property_type).and_then(|p| Some(p.clone())))
    }

    fn set_remote_device_property(
        &mut self,
        device: &BluetoothDevice,
        property_type: BtPropertyType,
        property: BluetoothProperty,
    ) -> Result<(), ()> {
        let remote_device = match self.get_remote_device_if_found_mut(&device.address) {
            Some(d) => d,
            None => {
                return Err(());
            }
        };

        let mut addr = RawAddress::from_string(device.address.clone());
        if addr.is_none() {
            return Err(());
        }
        let addr = addr.as_mut().unwrap();

        // TODO: Determine why a callback isn't invoked to do this.
        remote_device.properties.insert(property_type, property.clone());
        self.intf.lock().unwrap().set_remote_device_property(addr, property);
        Ok(())
    }

    /// Returns whether the adapter is connectable.
    pub(crate) fn get_connectable_internal(&self) -> bool {
        match self.properties.get(&BtPropertyType::AdapterScanMode) {
            Some(prop) => match prop {
                BluetoothProperty::AdapterScanMode(mode) => match *mode {
                    BtScanMode::Connectable | BtScanMode::ConnectableDiscoverable => true,
                    _ => false,
                },
                _ => false,
            },
            _ => false,
        }
    }

    /// Sets the adapter's connectable mode for classic connections.
    pub(crate) fn set_connectable_internal(&mut self, mode: bool) -> bool {
        self.is_connectable = mode;
        if mode && self.get_discoverable() {
            return true;
        }
        self.intf.lock().unwrap().set_adapter_property(BluetoothProperty::AdapterScanMode(
            if mode { BtScanMode::Connectable } else { BtScanMode::None_ },
        )) == 0
    }

    /// Returns adapter's discoverable mode.
    pub fn get_discoverable_mode_internal(&self) -> BtDiscMode {
        let off_mode = BtDiscMode::NonDiscoverable;

        match self.properties.get(&BtPropertyType::AdapterScanMode) {
            Some(prop) => match prop {
                BluetoothProperty::AdapterScanMode(mode) => match *mode {
                    BtScanMode::ConnectableDiscoverable => BtDiscMode::GeneralDiscoverable,
                    BtScanMode::ConnectableLimitedDiscoverable => BtDiscMode::LimitedDiscoverable,
                    _ => off_mode,
                },
                _ => off_mode,
            },
            _ => off_mode,
        }
    }

    /// Returns adapter's alias.
    pub(crate) fn get_alias_internal(&self) -> String {
        let name = self.get_name();
        if !name.is_empty() {
            return name;
        }

        // If the adapter name is empty, generate one based on local BDADDR
        // so that test programs can have a friendly name for the adapter.
        match self.local_address {
            None => "floss_0000".to_string(),
            Some(addr) => format!("floss_{:02X}{:02X}", addr.address[4], addr.address[5]),
        }
    }

    pub(crate) fn get_hid_report_internal(
        &mut self,
        addr: String,
        report_type: BthhReportType,
        report_id: u8,
    ) -> BtStatus {
        if let Some(mut addr) = RawAddress::from_string(addr) {
            self.hh.as_mut().unwrap().get_report(&mut addr, report_type, report_id, 128)
        } else {
            BtStatus::InvalidParam
        }
    }

    pub(crate) fn set_hid_report_internal(
        &mut self,
        addr: String,
        report_type: BthhReportType,
        report: String,
    ) -> BtStatus {
        if let Some(mut addr) = RawAddress::from_string(addr) {
            let mut rb = report.clone().into_bytes();
            self.hh.as_mut().unwrap().set_report(&mut addr, report_type, rb.as_mut_slice())
        } else {
            BtStatus::InvalidParam
        }
    }

    pub(crate) fn send_hid_data_internal(&mut self, addr: String, data: String) -> BtStatus {
        if let Some(mut addr) = RawAddress::from_string(addr) {
            let mut rb = data.clone().into_bytes();
            self.hh.as_mut().unwrap().send_data(&mut addr, rb.as_mut_slice())
        } else {
            BtStatus::InvalidParam
        }
    }

    /// Returns all bonded and connected devices.
    pub(crate) fn get_bonded_and_connected_devices(&mut self) -> Vec<BluetoothDevice> {
        self.bonded_devices
            .values()
            .filter(|v| v.acl_state == BtAclState::Connected && v.bond_state == BtBondState::Bonded)
            .map(|v| v.info.clone())
            .collect()
    }

    /// Gets the bond state of a single device with its address.
    pub fn get_bond_state_by_addr(&self, addr: &String) -> BtBondState {
        match self.bonded_devices.get(addr) {
            Some(device) => device.bond_state.clone(),
            None => BtBondState::NotBonded,
        }
    }

    /// Check whether found devices are still fresh. If they're outside the
    /// freshness window, send a notification to clear the device from clients.
    fn trigger_freshness_check(&mut self) {
        // A found device is considered fresh if:
        // * It was last seen less than |FOUND_DEVICE_FRESHNESS| ago.
        // * It is currently connected.
        fn is_fresh(d: &BluetoothDeviceContext, now: &Instant) -> bool {
            let fresh_at = d.last_seen + FOUND_DEVICE_FRESHNESS;
            now < &fresh_at || d.acl_state == BtAclState::Connected
        }

        let now = Instant::now();
        let stale_devices: Vec<BluetoothDevice> = self
            .found_devices
            .iter()
            .filter(|(_, d)| !is_fresh(d, &now))
            .map(|(_, d)| d.info.clone())
            .collect();

        // Retain only devices that are fresh.
        self.found_devices.retain(|_, d| is_fresh(d, &now));

        for d in stale_devices {
            self.callbacks.for_all_callbacks(|callback| {
                callback.on_device_cleared(d.clone());
            });

            self.bluetooth_admin.lock().unwrap().on_device_cleared(&d);
        }
    }

    /// Makes an LE_RAND call to the Bluetooth interface.
    pub fn le_rand(&mut self) -> bool {
        self.intf.lock().unwrap().le_rand() == BTM_SUCCESS
    }

    fn send_metrics_remote_device_info(device: &BluetoothDeviceContext) {
        if device.bond_state != BtBondState::Bonded && device.acl_state != BtAclState::Connected {
            return;
        }

        let addr = RawAddress::from_string(device.info.address.clone()).unwrap();
        let mut class_of_device = 0u32;
        let mut device_type = BtDeviceType::Unknown;
        let mut appearance = 0u16;
        let mut vpi =
            BtVendorProductInfo { vendor_id_src: 0, vendor_id: 0, product_id: 0, version: 0 };

        for prop in device.properties.values() {
            match prop {
                BluetoothProperty::TypeOfDevice(p) => device_type = p.clone(),
                BluetoothProperty::ClassOfDevice(p) => class_of_device = p.clone(),
                BluetoothProperty::Appearance(p) => appearance = p.clone(),
                BluetoothProperty::VendorProductInfo(p) => vpi = p.clone(),
                _ => (),
            }
        }

        metrics::device_info_report(
            addr,
            device_type,
            class_of_device,
            appearance,
            vpi.vendor_id,
            vpi.vendor_id_src,
            vpi.product_id,
            vpi.version,
        );
    }

    /// Handle some delayed and recurring actions within the adapter.
    pub(crate) fn handle_delayed_actions(&mut self, action: DelayedActions) {
        match action {
            DelayedActions::DeviceFreshnessCheck => {
                self.trigger_freshness_check();
            }

            DelayedActions::ConnectAllProfiles(device) => {
                self.connect_all_enabled_profiles(device);
            }

            DelayedActions::BleDiscoveryScannerRegistered(uuid, scanner_id, status) => {
                if let Some(app_uuid) = self.ble_scanner_uuid {
                    if app_uuid == uuid {
                        if status == GattStatus::Success {
                            self.ble_scanner_id = Some(scanner_id);
                        } else {
                            log::error!("BLE discovery scanner failed to register: {:?}", status);
                        }
                    }
                }
            }

            DelayedActions::BleDiscoveryScannerResult(result) => {
                let addr = RawAddress::from_string(result.address);

                let properties = match addr {
                    Some(v) => {
                        let mut props = vec![];
                        props.push(BluetoothProperty::BdName(result.name.clone()));
                        props.push(BluetoothProperty::BdAddr(v.clone()));
                        if result.service_uuids.len() > 0 {
                            props.push(BluetoothProperty::Uuids(
                                result
                                    .service_uuids
                                    .iter()
                                    .map(|&v| Uuid::from(v.clone()))
                                    .collect(),
                            ));
                        }
                        props.push(BluetoothProperty::RemoteRssi(result.rssi));
                        props.push(BluetoothProperty::RemoteAddrType(
                            (result.addr_type as u32).into(),
                        ));

                        props
                    }
                    None => {
                        return;
                    }
                };

                // Generate a vector of properties from ScanResult.
                let device = BluetoothDevice::from_properties(&properties);
                let address = device.address.clone();

                if let Some(existing) = self.found_devices.get_mut(&address) {
                    existing.update_properties(&properties);
                    existing.seen();
                } else {
                    let device_with_props = BluetoothDeviceContext::new(
                        BtBondState::NotBonded,
                        BtAclState::Disconnected,
                        device,
                        Instant::now(),
                        properties,
                    );
                    self.found_devices.insert(address.clone(), device_with_props);
                }
            }
        }
    }

    /// Creates a file to notify btmanagerd the adapter is enabled.
    fn create_pid_file(&self) -> std::io::Result<()> {
        let file_name = format!("{}/bluetooth{}.pid", PID_DIR, self.virt_index);
        let mut f = File::create(&file_name)?;
        f.write_all(process::id().to_string().as_bytes())?;
        Ok(())
    }

    /// Removes the file to notify btmanagerd the adapter is disabled.
    fn remove_pid_file(&self) -> std::io::Result<()> {
        let file_name = format!("{}/bluetooth{}.pid", PID_DIR, self.virt_index);
        std::fs::remove_file(&file_name)?;
        Ok(())
    }

    /// Set the suspend mode.
    pub fn set_discovery_suspend_mode(&mut self, suspend_mode: SuspendMode) {
        if suspend_mode != self.discovery_suspend_mode {
            self.discovery_suspend_mode = suspend_mode;
        }
    }

    /// Gets current suspend mode.
    pub fn get_discovery_suspend_mode(&self) -> SuspendMode {
        self.discovery_suspend_mode.clone()
    }

    /// Enters the suspend mode for discovery.
    pub fn discovery_enter_suspend(&mut self) -> BtStatus {
        if self.get_discovery_suspend_mode() != SuspendMode::Normal {
            return BtStatus::Busy;
        }
        self.set_discovery_suspend_mode(SuspendMode::Suspending);

        if self.is_discovering {
            self.is_discovering_before_suspend = true;
            self.cancel_discovery();
        }
        self.set_discovery_suspend_mode(SuspendMode::Suspended);

        return BtStatus::Success;
    }

    /// Exits the suspend mode for discovery.
    pub fn discovery_exit_suspend(&mut self) -> BtStatus {
        if self.get_discovery_suspend_mode() != SuspendMode::Suspended {
            return BtStatus::Busy;
        }
        self.set_discovery_suspend_mode(SuspendMode::Resuming);

        if self.is_discovering_before_suspend {
            self.is_discovering_before_suspend = false;
            self.start_discovery();
        }
        self.set_discovery_suspend_mode(SuspendMode::Normal);

        return BtStatus::Success;
    }

    /// Temporarily stop the discovery process and mark it as paused so that clients cannot restart
    /// it.
    fn pause_discovery(&mut self) {
        self.cancel_discovery();
        self.is_discovery_paused = true;
    }

    /// Remove the paused flag to allow clients to begin discovery, and if there is already a
    /// pending request, start discovery.
    fn resume_discovery(&mut self) {
        self.is_discovery_paused = false;
        if self.pending_discovery {
            self.pending_discovery = false;
            self.start_discovery();
        }
    }

    /// Return if there are wake-allowed device in bonded status.
    fn get_wake_allowed_device_bonded(&self) -> bool {
        self.get_bonded_devices().into_iter().any(|d| self.get_remote_wake_allowed(d))
    }

    /// Powerd recognizes bluetooth activities as valid wakeup sources if powerd keeps bluetooth in
    /// the monitored path. This only happens if there is at least one valid wake-allowed BT device
    /// connected during the suspending process. If there is no BT devices connected at any time
    /// during the suspending process, the wakeup count will be lost, and system goes to dark
    /// resume instead of full resume.
    /// Bluetooth stack disconnects all physical bluetooth HID devices for suspend, so a virtual
    /// uhid device is necessary to keep bluetooth as a valid wakeup source.
    fn create_uhid_for_suspend_wakesource(&mut self) {
        if !self.uhid_wakeup_source.is_empty() {
            return;
        }
        let adapter_addr = self.get_address().to_lowercase();
        match self.uhid_wakeup_source.create(
            "VIRTUAL_SUSPEND_UHID".to_string(),
            adapter_addr,
            String::from(BD_ADDR_DEFAULT),
        ) {
            Err(e) => log::error!("Fail to create uhid {}", e),
            Ok(_) => (),
        }
    }

    /// Clear the UHID device.
    fn clear_uhid(&mut self) {
        self.uhid_wakeup_source.clear();
    }
}

#[btif_callbacks_dispatcher(dispatch_base_callbacks, BaseCallbacks)]
#[allow(unused_variables)]
pub(crate) trait BtifBluetoothCallbacks {
    #[btif_callback(AdapterState)]
    fn adapter_state_changed(&mut self, state: BtState) {}

    #[btif_callback(AdapterProperties)]
    fn adapter_properties_changed(
        &mut self,
        status: BtStatus,
        num_properties: i32,
        properties: Vec<BluetoothProperty>,
    ) {
    }

    #[btif_callback(DeviceFound)]
    fn device_found(&mut self, n: i32, properties: Vec<BluetoothProperty>) {}

    #[btif_callback(DiscoveryState)]
    fn discovery_state(&mut self, state: BtDiscoveryState) {}

    #[btif_callback(SspRequest)]
    fn ssp_request(
        &mut self,
        remote_addr: RawAddress,
        remote_name: String,
        cod: u32,
        variant: BtSspVariant,
        passkey: u32,
    ) {
    }

    #[btif_callback(BondState)]
    fn bond_state(
        &mut self,
        status: BtStatus,
        addr: RawAddress,
        bond_state: BtBondState,
        fail_reason: i32,
    ) {
    }

    #[btif_callback(RemoteDeviceProperties)]
    fn remote_device_properties_changed(
        &mut self,
        status: BtStatus,
        addr: RawAddress,
        num_properties: i32,
        properties: Vec<BluetoothProperty>,
    ) {
    }

    #[btif_callback(AclState)]
    fn acl_state(
        &mut self,
        status: BtStatus,
        addr: RawAddress,
        state: BtAclState,
        link_type: BtTransport,
        hci_reason: BtHciErrorCode,
        conn_direction: BtConnectionDirection,
        acl_handle: u16,
    ) {
    }

    #[btif_callback(LeRandCallback)]
    fn le_rand_cb(&mut self, random: u64) {}

    #[btif_callback(PinRequest)]
    fn pin_request(
        &mut self,
        remote_addr: RawAddress,
        remote_name: String,
        cod: u32,
        min_16_digit: bool,
    ) {
    }

    #[btif_callback(ThreadEvent)]
    fn thread_event(&mut self, event: BtThreadEvent) {}
}

#[btif_callbacks_dispatcher(dispatch_hid_host_callbacks, HHCallbacks)]
pub(crate) trait BtifHHCallbacks {
    #[btif_callback(ConnectionState)]
    fn connection_state(&mut self, address: RawAddress, state: BthhConnectionState);

    #[btif_callback(HidInfo)]
    fn hid_info(&mut self, address: RawAddress, info: BthhHidInfo);

    #[btif_callback(ProtocolMode)]
    fn protocol_mode(&mut self, address: RawAddress, status: BthhStatus, mode: BthhProtocolMode);

    #[btif_callback(IdleTime)]
    fn idle_time(&mut self, address: RawAddress, status: BthhStatus, idle_rate: i32);

    #[btif_callback(GetReport)]
    fn get_report(&mut self, address: RawAddress, status: BthhStatus, data: Vec<u8>, size: i32);

    #[btif_callback(Handshake)]
    fn handshake(&mut self, address: RawAddress, status: BthhStatus);
}

#[btif_callbacks_dispatcher(dispatch_sdp_callbacks, SdpCallbacks)]
pub(crate) trait BtifSdpCallbacks {
    #[btif_callback(SdpSearch)]
    fn sdp_search(
        &mut self,
        status: BtStatus,
        address: RawAddress,
        uuid: Uuid,
        count: i32,
        records: Vec<BtSdpRecord>,
    );
}

pub fn get_bt_dispatcher(tx: Sender<Message>) -> BaseCallbacksDispatcher {
    BaseCallbacksDispatcher {
        dispatch: Box::new(move |cb| {
            let txl = tx.clone();
            topstack::get_runtime().spawn(async move {
                let _ = txl.send(Message::Base(cb)).await;
            });
        }),
    }
}

impl BtifBluetoothCallbacks for Bluetooth {
    fn adapter_state_changed(&mut self, state: BtState) {
        let prev_state = self.state.clone();
        self.state = state;
        metrics::adapter_state_changed(self.state.clone());

        // If it's the same state as before, no further action
        if self.state == prev_state {
            return;
        }

        match self.state {
            BtState::Off => {
                self.properties.clear();
                match self.remove_pid_file() {
                    Err(err) => warn!("remove_pid_file() error: {}", err),
                    _ => (),
                }

                self.clear_uhid();

                // Let the signal notifier know we are turned off.
                *self.sig_notifier.enabled.lock().unwrap() = false;
                self.sig_notifier.enabled_notify.notify_all();
            }

            BtState::On => {
                // Initialize media
                self.bluetooth_media.lock().unwrap().initialize();

                // Initialize core profiles
                self.init_profiles();

                // Trigger properties update
                self.intf.lock().unwrap().get_adapter_properties();

                // Also need to manually request some properties
                self.intf.lock().unwrap().get_adapter_property(BtPropertyType::ClassOfDevice);
                self.le_supported_states = controller::Controller::new().get_ble_supported_states();

                // Initialize the BLE scanner for discovery.
                let callback_id = self.bluetooth_gatt.lock().unwrap().register_scanner_callback(
                    Box::new(BleDiscoveryCallbacks::new(self.tx.clone())),
                );
                self.ble_scanner_uuid =
                    Some(self.bluetooth_gatt.lock().unwrap().register_scanner(callback_id));

                // Ensure device is connectable so that disconnected device can reconnect
                self.set_connectable(true);

                // Spawn a freshness check job in the background.
                self.freshness_check.take().map(|h| h.abort());
                let txl = self.tx.clone();
                self.freshness_check = Some(tokio::spawn(async move {
                    loop {
                        time::sleep(FOUND_DEVICE_FRESHNESS).await;
                        let _ = txl
                            .send(Message::DelayedAdapterActions(
                                DelayedActions::DeviceFreshnessCheck,
                            ))
                            .await;
                    }
                }));

                if self.get_wake_allowed_device_bonded() {
                    self.create_uhid_for_suspend_wakesource();
                }
                // Notify the signal notifier that we are turned on.
                *self.sig_notifier.enabled.lock().unwrap() = true;
                self.sig_notifier.enabled_notify.notify_all();

                // Signal that the stack is up and running.
                match self.create_pid_file() {
                    Err(err) => warn!("create_pid_file() error: {}", err),
                    _ => (),
                }

                // Inform the rest of the stack we're ready.
                let txl = self.tx.clone();
                let api_txl = self.api_tx.clone();
                tokio::spawn(async move {
                    let _ = txl.send(Message::AdapterReady).await;
                });
                tokio::spawn(async move {
                    let _ = api_txl.send(APIMessage::IsReady(BluetoothAPI::Adapter)).await;
                    // TODO(b:300202052) make sure media interface is exposed after initialized
                    let _ = api_txl.send(APIMessage::IsReady(BluetoothAPI::Media)).await;
                });
            }
        }
    }

    #[allow(unused_variables)]
    fn adapter_properties_changed(
        &mut self,
        status: BtStatus,
        num_properties: i32,
        properties: Vec<BluetoothProperty>,
    ) {
        if status != BtStatus::Success {
            return;
        }

        // Update local property cache
        for prop in properties {
            self.properties.insert(prop.get_type(), prop.clone());

            match &prop {
                BluetoothProperty::BdAddr(bdaddr) => {
                    self.update_local_address(&bdaddr);
                }
                BluetoothProperty::AdapterBondedDevices(bondlist) => {
                    for addr in bondlist.iter() {
                        let address = addr.to_string();

                        // Update bonded state if already in the list. Otherwise create a new
                        // context with empty properties and name.
                        self.bonded_devices
                            .entry(address.clone())
                            .and_modify(|d| d.bond_state = BtBondState::Bonded)
                            .or_insert(BluetoothDeviceContext::new(
                                BtBondState::Bonded,
                                BtAclState::Disconnected,
                                BluetoothDevice::new(address.clone(), "".to_string()),
                                Instant::now(),
                                vec![],
                            ));
                    }
                }
                BluetoothProperty::BdName(bdname) => {
                    self.callbacks.for_all_callbacks(|callback| {
                        callback.on_name_changed(bdname.clone());
                    });
                }
                BluetoothProperty::AdapterScanMode(mode) => {
                    self.callbacks.for_all_callbacks(|callback| {
                        callback
                            .on_discoverable_changed(*mode == BtScanMode::ConnectableDiscoverable);
                    });
                }
                _ => {}
            }

            self.callbacks.for_all_callbacks(|callback| {
                callback.on_adapter_property_changed(prop.get_type());
            });
        }
    }

    fn device_found(&mut self, _n: i32, properties: Vec<BluetoothProperty>) {
        let device = BluetoothDevice::from_properties(&properties);
        let address = device.address.clone();

        if let Some(existing) = self.found_devices.get_mut(&address) {
            existing.update_properties(&properties);
            existing.seen();
        } else {
            let device_with_props = BluetoothDeviceContext::new(
                BtBondState::NotBonded,
                BtAclState::Disconnected,
                device,
                Instant::now(),
                properties,
            );
            self.found_devices.insert(address.clone(), device_with_props);
        }

        let device = self.found_devices.get(&address).unwrap();

        self.callbacks.for_all_callbacks(|callback| {
            callback.on_device_found(device.info.clone());
        });

        self.bluetooth_admin.lock().unwrap().on_device_found(&device.info);
    }

    fn discovery_state(&mut self, state: BtDiscoveryState) {
        let is_discovering = &state == &BtDiscoveryState::Started;

        // No-op if we're updating the state to the same value again.
        if &is_discovering == &self.is_discovering {
            return;
        }

        // Cache discovering state
        self.is_discovering = &state == &BtDiscoveryState::Started;
        if self.is_discovering {
            self.discovering_started = Instant::now();
        }

        // Prevent sending out discovering changes or freshness checks when
        // suspending. Clients don't need to be notified of discovery pausing
        // during suspend. They will probably try to restore it and fail.
        let discovery_suspend_mode = self.get_discovery_suspend_mode();
        if discovery_suspend_mode != SuspendMode::Normal
            && discovery_suspend_mode != SuspendMode::Resuming
        {
            return;
        }

        self.callbacks.for_all_callbacks(|callback| {
            callback.on_discovering_changed(state == BtDiscoveryState::Started);
        });

        // Start or stop BLE scanning based on discovering state
        if let Some(scanner_id) = self.ble_scanner_id {
            if is_discovering {
                self.bluetooth_gatt.lock().unwrap().start_active_scan(scanner_id);
            } else {
                self.bluetooth_gatt.lock().unwrap().stop_active_scan(scanner_id);
            }
        }
    }

    fn ssp_request(
        &mut self,
        remote_addr: RawAddress,
        remote_name: String,
        cod: u32,
        variant: BtSspVariant,
        passkey: u32,
    ) {
        // Accept the Just-Works pairing that we initiated, reject otherwise.
        if variant == BtSspVariant::Consent {
            let initiated_by_us = Some(remote_addr.clone()) == self.active_pairing_address;
            self.set_pairing_confirmation(
                BluetoothDevice::new(remote_addr.to_string(), remote_name.clone()),
                initiated_by_us,
            );
            return;
        }

        // Currently this supports many agent because we accept many callbacks.
        // TODO(b/274706838): We need a way to select the default agent.
        self.callbacks.for_all_callbacks(|callback| {
            callback.on_ssp_request(
                BluetoothDevice::new(remote_addr.to_string(), remote_name.clone()),
                cod,
                variant.clone(),
                passkey,
            );
        });
    }

    fn pin_request(
        &mut self,
        remote_addr: RawAddress,
        remote_name: String,
        cod: u32,
        min_16_digit: bool,
    ) {
        let device = BluetoothDevice::new(remote_addr.to_string(), remote_name.clone());

        let digits = match min_16_digit {
            true => 16,
            false => 6,
        };

        if is_cod_hid_keyboard(cod) || is_cod_hid_combo(cod) {
            debug!("auto gen pin for device {} (cod={:#x})", DisplayAddress(&remote_addr), cod);
            // generate a random pin code to display.
            let pin = rand::random::<u64>() % pow(10, digits);
            let display_pin = format!("{:06}", pin);

            // Currently this supports many agent because we accept many callbacks.
            // TODO(b/274706838): We need a way to select the default agent.
            self.callbacks.for_all_callbacks(|callback| {
                callback.on_pin_display(device.clone(), display_pin.clone());
            });

            let pin_vec = display_pin.chars().map(|d| d.try_into().unwrap()).collect::<Vec<u8>>();

            self.set_pin(device, true, pin_vec);
        } else {
            debug!(
                "sending pin request for device {} (cod={:#x}) to clients",
                DisplayAddress(&remote_addr),
                cod
            );
            // Currently this supports many agent because we accept many callbacks.
            // TODO(b/274706838): We need a way to select the default agent.
            self.callbacks.for_all_callbacks(|callback| {
                callback.on_pin_request(device.clone(), cod, min_16_digit);
            });
        }
    }

    fn bond_state(
        &mut self,
        status: BtStatus,
        addr: RawAddress,
        bond_state: BtBondState,
        fail_reason: i32,
    ) {
        let address = addr.to_string();

        // Get the device type before the device is potentially deleted.
        let device_type =
            self.get_remote_type(BluetoothDevice::new(address.clone(), "".to_string()));

        // Clear the pairing lock if this call corresponds to the
        // active pairing device.
        if &bond_state != &BtBondState::Bonding && self.active_pairing_address == Some(addr) {
            self.active_pairing_address = None;
        }

        // Easy case of not bonded -- we remove the device from the bonded list and change the bond
        // state in the found list (in case it was previously bonding).
        if &bond_state == &BtBondState::NotBonded {
            self.bonded_devices.remove(&address);
            self.found_devices
                .entry(address.clone())
                .and_modify(|d| d.bond_state = bond_state.clone());
            if !self.get_wake_allowed_device_bonded() {
                self.clear_uhid();
            }
        }
        // We will only insert into the bonded list after bonding is complete
        else if &bond_state == &BtBondState::Bonded && !self.bonded_devices.contains_key(&address)
        {
            // We either need to construct a new BluetoothDeviceContext or grab it from the found
            // devices map. Immediately insert that into the bonded list.
            let mut device = match self.found_devices.remove(&address) {
                Some(mut v) => {
                    v.bond_state = bond_state.clone();
                    v
                }
                None => BluetoothDeviceContext::new(
                    bond_state.clone(),
                    BtAclState::Disconnected,
                    BluetoothDevice::new(address.clone(), "".to_string()),
                    Instant::now(),
                    vec![],
                ),
            };
            let device_info = device.info.clone();

            // Since this is a newly bonded device, we also need to trigger SDP
            // on it.
            device.services_resolved = false;
            self.bonded_devices.insert(address.clone(), device);
            self.fetch_remote_uuids(device_info);
            if self.get_wake_allowed_device_bonded() {
                self.create_uhid_for_suspend_wakesource();
            }
        } else {
            // If we're bonding, we need to update the found devices list
            self.found_devices
                .entry(address.clone())
                .and_modify(|d| d.bond_state = bond_state.clone());
        }

        // Resume discovery once the bonding process is complete. Discovery was paused before the
        // bond request to avoid ACL connection from interfering with active inquiry.
        if &bond_state == &BtBondState::NotBonded || &bond_state == &BtBondState::Bonded {
            self.resume_discovery();
        }

        // Send bond state changed notifications
        self.callbacks.for_all_callbacks(|callback| {
            callback.on_bond_state_changed(
                status.to_u32().unwrap(),
                address.clone(),
                bond_state.to_u32().unwrap(),
            );
        });

        // Don't emit the metrics event if we were cancelling the bond.
        // It is ok to not send the pairing complete event as the server should ignore the dangling
        // pairing attempt event.
        // This behavior aligns with BlueZ.
        if !self.cancelling_devices.remove(&addr) {
            metrics::bond_state_changed(addr, device_type, status, bond_state, fail_reason);
        }
    }

    fn remote_device_properties_changed(
        &mut self,
        _status: BtStatus,
        addr: RawAddress,
        _num_properties: i32,
        properties: Vec<BluetoothProperty>,
    ) {
        let address = addr.to_string();
        let txl = self.tx.clone();
        let device = match self.get_remote_device_if_found_mut(&address) {
            None => {
                self.found_devices.insert(
                    address.clone(),
                    BluetoothDeviceContext::new(
                        BtBondState::NotBonded,
                        BtAclState::Disconnected,
                        BluetoothDevice::new(address.clone(), String::from("")),
                        Instant::now(),
                        vec![],
                    ),
                );

                self.found_devices.get_mut(&address)
            }
            some => some,
        };

        match device {
            Some(d) => {
                d.update_properties(&properties);
                d.seen();

                Bluetooth::send_metrics_remote_device_info(d);

                let info = d.info.clone();

                if !d.services_resolved {
                    let has_uuids = properties.iter().any(|prop| match prop {
                        BluetoothProperty::Uuids(uu) => uu.len() > 0,
                        _ => false,
                    });

                    // Services are resolved when uuids are fetched.
                    d.services_resolved |= has_uuids;
                }

                if d.wait_to_connect && d.services_resolved {
                    d.wait_to_connect = false;

                    let sent_info = info.clone();
                    tokio::spawn(async move {
                        let _ = txl
                            .send(Message::DelayedAdapterActions(
                                DelayedActions::ConnectAllProfiles(sent_info),
                            ))
                            .await;
                    });
                }

                let info = &d.info.clone();
                self.callbacks.for_all_callbacks(|callback| {
                    callback.on_device_properties_changed(
                        info.clone(),
                        properties.clone().into_iter().map(|x| x.get_type()).collect(),
                    );
                });

                self.bluetooth_admin
                    .lock()
                    .unwrap()
                    .on_remote_device_properties_changed(&info, &properties);
            }
            None => (),
        }
    }

    fn acl_state(
        &mut self,
        status: BtStatus,
        addr: RawAddress,
        state: BtAclState,
        link_type: BtTransport,
        hci_reason: BtHciErrorCode,
        conn_direction: BtConnectionDirection,
        _acl_handle: u16,
    ) {
        // If discovery was previously paused at connect_all_enabled_profiles to avoid an outgoing
        // ACL connection colliding with an ongoing inquiry, resume it.
        self.resume_discovery();

        if status != BtStatus::Success {
            warn!(
                "Connection to [{}] failed. Status: {:?}, Reason: {:?}",
                DisplayAddress(&addr),
                status,
                hci_reason
            );
            metrics::acl_connection_state_changed(
                addr,
                link_type,
                status,
                BtAclState::Disconnected,
                conn_direction,
                hci_reason,
            );
            return;
        }

        let address = addr.to_string();
        let device = match self.get_remote_device_if_found_mut(&address) {
            None => {
                self.found_devices.insert(
                    address.clone(),
                    BluetoothDeviceContext::new(
                        BtBondState::NotBonded,
                        BtAclState::Disconnected,
                        BluetoothDevice::new(address.clone(), String::from("")),
                        Instant::now(),
                        vec![],
                    ),
                );

                self.found_devices.get_mut(&address)
            }
            some => some,
        };

        match device {
            Some(found) => {
                // Only notify if there's been a change in state
                let prev_state = &found.acl_state;
                if prev_state != &state {
                    let device = found.info.clone();
                    found.acl_state = state.clone();
                    found.acl_reported_transport = link_type;

                    metrics::acl_connection_state_changed(
                        addr,
                        link_type,
                        BtStatus::Success,
                        state.clone(),
                        conn_direction,
                        hci_reason,
                    );

                    match state {
                        BtAclState::Connected => {
                            let bluetooth_device = found.info.clone();
                            let acl_reported_transport = found.acl_reported_transport.clone();
                            Bluetooth::send_metrics_remote_device_info(found);
                            self.connection_callbacks.for_all_callbacks(|callback| {
                                callback.on_device_connected(device.clone());
                            });
                            let tx = self.tx.clone();
                            let transport = match self.get_remote_type(bluetooth_device.clone()) {
                                BtDeviceType::Bredr => BtTransport::Bredr,
                                BtDeviceType::Ble => BtTransport::Le,
                                _ => acl_reported_transport,
                            };
                            tokio::spawn(async move {
                                let _ = tx
                                    .send(Message::OnAclConnected(bluetooth_device, transport))
                                    .await;
                            });
                        }
                        BtAclState::Disconnected => {
                            self.connection_callbacks.for_all_callbacks(|callback| {
                                callback.on_device_disconnected(device.clone());
                            });
                            let tx = self.tx.clone();
                            tokio::spawn(async move {
                                let _ = tx.send(Message::OnAclDisconnected(device.clone())).await;
                            });
                        }
                    };
                }
            }
            None => (),
        };
    }

    fn thread_event(&mut self, event: BtThreadEvent) {
        match event {
            BtThreadEvent::Associate => {
                // Let the signal notifier know stack is initialized.
                *self.sig_notifier.thread_attached.lock().unwrap() = true;
                self.sig_notifier.thread_notify.notify_all();
            }
            BtThreadEvent::Disassociate => {
                // Let the signal notifier know stack is done.
                *self.sig_notifier.thread_attached.lock().unwrap() = false;
                self.sig_notifier.thread_notify.notify_all();
            }
        }
    }
}

struct BleDiscoveryCallbacks {
    tx: Sender<Message>,
}

impl BleDiscoveryCallbacks {
    fn new(tx: Sender<Message>) -> Self {
        Self { tx }
    }
}

// Handle BLE scanner results.
impl IScannerCallback for BleDiscoveryCallbacks {
    fn on_scanner_registered(&mut self, uuid: Uuid128Bit, scanner_id: u8, status: GattStatus) {
        let tx = self.tx.clone();
        tokio::spawn(async move {
            let _ = tx
                .send(Message::DelayedAdapterActions(
                    DelayedActions::BleDiscoveryScannerRegistered(uuid, scanner_id, status),
                ))
                .await;
        });
    }

    fn on_scan_result(&mut self, scan_result: ScanResult) {
        let tx = self.tx.clone();
        tokio::spawn(async move {
            let _ = tx
                .send(Message::DelayedAdapterActions(DelayedActions::BleDiscoveryScannerResult(
                    scan_result,
                )))
                .await;
        });
    }

    fn on_advertisement_found(&mut self, _scanner_id: u8, _scan_result: ScanResult) {}
    fn on_advertisement_lost(&mut self, _scanner_id: u8, _scan_result: ScanResult) {}
    fn on_suspend_mode_change(&mut self, _suspend_mode: SuspendMode) {}
}

impl RPCProxy for BleDiscoveryCallbacks {
    fn get_object_id(&self) -> String {
        "BLE Discovery Callback".to_string()
    }
}

// TODO: Add unit tests for this implementation
impl IBluetooth for Bluetooth {
    fn register_callback(&mut self, callback: Box<dyn IBluetoothCallback + Send>) -> u32 {
        self.callbacks.add_callback(callback)
    }

    fn unregister_callback(&mut self, callback_id: u32) -> bool {
        self.callbacks.remove_callback(callback_id)
    }

    fn register_connection_callback(
        &mut self,
        callback: Box<dyn IBluetoothConnectionCallback + Send>,
    ) -> u32 {
        self.connection_callbacks.add_callback(callback)
    }

    fn unregister_connection_callback(&mut self, callback_id: u32) -> bool {
        self.connection_callbacks.remove_callback(callback_id)
    }

    fn init(&mut self, init_flags: Vec<String>) -> bool {
        self.intf.lock().unwrap().initialize(get_bt_dispatcher(self.tx.clone()), init_flags)
    }

    fn enable(&mut self) -> bool {
        self.intf.lock().unwrap().enable() == 0
    }

    fn disable(&mut self) -> bool {
        let success = self.intf.lock().unwrap().disable() == 0;
        if success {
            self.bluetooth_gatt.lock().unwrap().enable(false);
        }
        success
    }

    fn cleanup(&mut self) {
        self.intf.lock().unwrap().cleanup();
    }

    fn get_address(&self) -> String {
        match self.local_address {
            None => String::from(""),
            Some(addr) => addr.to_string(),
        }
    }

    fn get_uuids(&self) -> Vec<Uuid128Bit> {
        match self.properties.get(&BtPropertyType::Uuids) {
            Some(prop) => match prop {
                BluetoothProperty::Uuids(uuids) => {
                    uuids.iter().map(|&x| x.uu.clone()).collect::<Vec<Uuid128Bit>>()
                }
                _ => vec![],
            },
            _ => vec![],
        }
    }

    fn get_name(&self) -> String {
        match self.properties.get(&BtPropertyType::BdName) {
            Some(prop) => match prop {
                BluetoothProperty::BdName(name) => name.clone(),
                _ => String::new(),
            },
            _ => String::new(),
        }
    }

    fn set_name(&self, name: String) -> bool {
        self.intf.lock().unwrap().set_adapter_property(BluetoothProperty::BdName(name)) == 0
    }

    fn get_bluetooth_class(&self) -> u32 {
        match self.properties.get(&BtPropertyType::ClassOfDevice) {
            Some(prop) => match prop {
                BluetoothProperty::ClassOfDevice(cod) => cod.clone(),
                _ => 0,
            },
            _ => 0,
        }
    }

    fn set_bluetooth_class(&self, cod: u32) -> bool {
        self.intf.lock().unwrap().set_adapter_property(BluetoothProperty::ClassOfDevice(cod)) == 0
    }

    fn get_discoverable(&self) -> bool {
        match self.properties.get(&BtPropertyType::AdapterScanMode) {
            Some(prop) => match prop {
                BluetoothProperty::AdapterScanMode(mode) => match mode {
                    BtScanMode::ConnectableDiscoverable => true,
                    _ => false,
                },
                _ => false,
            },
            _ => false,
        }
    }

    fn get_discoverable_timeout(&self) -> u32 {
        match self.properties.get(&BtPropertyType::AdapterDiscoverableTimeout) {
            Some(prop) => match prop {
                BluetoothProperty::AdapterDiscoverableTimeout(timeout) => timeout.clone(),
                _ => 0,
            },
            _ => 0,
        }
    }

    fn set_discoverable(&mut self, mode: BtDiscMode, duration: u32) -> bool {
        let intf = self.intf.lock().unwrap();

        // Checks if the duration is valid.
        if mode == BtDiscMode::LimitedDiscoverable && (duration > 60 || duration <= 0) {
            warn!("Invalid duration for setting the device into limited discoverable mode. The valid duration is 1~60 seconds.");
            return false;
        }

        let off_mode =
            if self.is_connectable { BtScanMode::Connectable } else { BtScanMode::None_ };

        let new_mode = match mode {
            BtDiscMode::LimitedDiscoverable => BtScanMode::ConnectableLimitedDiscoverable,
            BtDiscMode::GeneralDiscoverable => BtScanMode::ConnectableDiscoverable,
            BtDiscMode::NonDiscoverable => off_mode.clone(),
        };

        // The old timer should be overwritten regardless of what the new mode is.
        if let Some(ref handle) = self.discoverable_timeout {
            handle.abort();
            self.discoverable_timeout = None;
        }

        if intf.set_adapter_property(BluetoothProperty::AdapterDiscoverableTimeout(duration)) != 0
            || intf.set_adapter_property(BluetoothProperty::AdapterScanMode(new_mode)) != 0
        {
            return false;
        }

        if (mode != BtDiscMode::NonDiscoverable) && (duration != 0) {
            let intf_clone = self.intf.clone();
            self.discoverable_timeout = Some(tokio::spawn(async move {
                time::sleep(Duration::from_secs(duration.into())).await;
                intf_clone
                    .lock()
                    .unwrap()
                    .set_adapter_property(BluetoothProperty::AdapterScanMode(off_mode));
            }));
        }

        true
    }

    fn is_multi_advertisement_supported(&self) -> bool {
        match self.properties.get(&BtPropertyType::LocalLeFeatures) {
            Some(prop) => match prop {
                BluetoothProperty::LocalLeFeatures(llf) => {
                    llf.max_adv_instance >= MIN_ADV_INSTANCES_FOR_MULTI_ADV
                }
                _ => false,
            },
            _ => false,
        }
    }

    fn is_le_extended_advertising_supported(&self) -> bool {
        match self.properties.get(&BtPropertyType::LocalLeFeatures) {
            Some(prop) => match prop {
                BluetoothProperty::LocalLeFeatures(llf) => llf.le_extended_advertising_supported,
                _ => false,
            },
            _ => false,
        }
    }

    fn start_discovery(&mut self) -> bool {
        // Short-circuit to avoid sending multiple start discovery calls.
        if self.is_discovering {
            return true;
        }

        // Short-circuit if paused and add the discovery intent to the queue.
        if self.is_discovery_paused {
            self.pending_discovery = true;
            debug!("Queue the discovery request during paused state");
            return true;
        }

        let discovery_suspend_mode = self.get_discovery_suspend_mode();
        if discovery_suspend_mode != SuspendMode::Normal
            && discovery_suspend_mode != SuspendMode::Resuming
        {
            log::warn!("start_discovery is not allowed when suspending or suspended.");
            return false;
        }

        self.intf.lock().unwrap().start_discovery() == 0
    }

    fn cancel_discovery(&mut self) -> bool {
        // Client no longer want to discover, clear the request
        if self.is_discovery_paused {
            self.pending_discovery = false;
            debug!("Cancel the discovery request during paused state");
        }

        // Reject the cancel discovery request if the underlying stack is not in a discovering
        // state. For example, previous start discovery was enqueued for ongoing discovery.
        if !self.is_discovering {
            debug!("Reject cancel_discovery as it's not in discovering state.");
            return false;
        }

        let discovery_suspend_mode = self.get_discovery_suspend_mode();
        if discovery_suspend_mode != SuspendMode::Normal
            && discovery_suspend_mode != SuspendMode::Suspending
        {
            log::warn!("cancel_discovery is not allowed when resuming or suspended.");
            return false;
        }

        self.intf.lock().unwrap().cancel_discovery() == 0
    }

    fn is_discovering(&self) -> bool {
        self.is_discovering
    }

    fn get_discovery_end_millis(&self) -> u64 {
        if !self.is_discovering {
            return 0;
        }

        let elapsed_ms = self.discovering_started.elapsed().as_millis() as u64;
        if elapsed_ms >= DEFAULT_DISCOVERY_TIMEOUT_MS {
            0
        } else {
            DEFAULT_DISCOVERY_TIMEOUT_MS - elapsed_ms
        }
    }

    fn create_bond(&mut self, device: BluetoothDevice, transport: BtTransport) -> bool {
        let addr = RawAddress::from_string(device.address.clone());

        if addr.is_none() {
            metrics::bond_create_attempt(RawAddress::default(), BtDeviceType::Unknown);
            metrics::bond_state_changed(
                RawAddress::default(),
                BtDeviceType::Unknown,
                BtStatus::InvalidParam,
                BtBondState::NotBonded,
                0,
            );
            warn!("Can't create bond. Address {} is not valid", device.address);
            return false;
        }

        let address = addr.unwrap();
        let device_type = match transport {
            BtTransport::Bredr => BtDeviceType::Bredr,
            BtTransport::Le => BtDeviceType::Ble,
            _ => self.get_remote_type(device.clone()),
        };

        if let Some(active_address) = self.active_pairing_address {
            warn!(
                "Bonding requested for {} while already bonding {}, rejecting",
                DisplayAddress(&address),
                DisplayAddress(&active_address)
            );
            return false;
        }

        // There could be a race between bond complete and bond cancel, which makes
        // |cancelling_devices| in a wrong state. Remove the device just in case.
        if self.cancelling_devices.remove(&address) {
            warn!("Device {} is also cancelling the bond.", DisplayAddress(&address));
        }

        // We explicitly log the attempt to start the bonding separate from logging the bond state.
        // The start of the attempt is critical to help identify a bonding/pairing session.
        metrics::bond_create_attempt(address, device_type.clone());

        // BREDR connection won't work when Inquiry is in progress.
        self.pause_discovery();

        self.active_pairing_address = Some(address.clone());
        let status = self.intf.lock().unwrap().create_bond(&address, transport);

        if status != 0 {
            metrics::bond_state_changed(
                address,
                device_type,
                BtStatus::from(status as u32),
                BtBondState::NotBonded,
                0,
            );
            return false;
        }

        // Creating bond automatically create ACL connection as well, therefore also log metrics
        // ACL connection attempt here.
        let is_connected = self
            .get_remote_device_if_found(&device.address)
            .map_or(false, |d| d.acl_state == BtAclState::Connected);
        if !is_connected {
            metrics::acl_connect_attempt(address, BtAclState::Connected);
        }

        return true;
    }

    fn cancel_bond_process(&mut self, device: BluetoothDevice) -> bool {
        let addr = RawAddress::from_string(device.address.clone());

        if addr.is_none() {
            warn!("Can't cancel bond. Address {} is not valid.", device.address);
            return false;
        }

        let address = addr.unwrap();
        if !self.cancelling_devices.insert(address.clone()) {
            warn!("Device {} has been added to cancelling_device.", DisplayAddress(&address));
        }

        self.intf.lock().unwrap().cancel_bond(&address) == 0
    }

    fn remove_bond(&mut self, device: BluetoothDevice) -> bool {
        let addr = RawAddress::from_string(device.address.clone());

        if addr.is_none() {
            warn!("Can't remove bond. Address {} is not valid.", device.address);
            return false;
        }

        let address = addr.unwrap();
        debug!("Removing bond for {}", DisplayAddress(&address));

        // There could be a race between bond complete and bond cancel, which makes
        // |cancelling_devices| in a wrong state. Remove the device just in case.
        if self.cancelling_devices.remove(&address) {
            warn!("Device {} is also cancelling the bond.", DisplayAddress(&address));
        }

        let status = self.intf.lock().unwrap().remove_bond(&address);

        if status != 0 {
            return false;
        }

        // Removing bond also disconnects the ACL if is connected. Therefore, also log ACL
        // disconnection attempt here.
        let is_connected = self
            .get_remote_device_if_found(&device.address)
            .map_or(false, |d| d.acl_state == BtAclState::Connected);
        if is_connected {
            metrics::acl_connect_attempt(address, BtAclState::Disconnected);
        }

        return true;
    }

    fn get_bonded_devices(&self) -> Vec<BluetoothDevice> {
        let mut devices: Vec<BluetoothDevice> = vec![];

        for (_, device) in self.bonded_devices.iter() {
            devices.push(device.info.clone());
        }

        devices
    }

    fn get_bond_state(&self, device: BluetoothDevice) -> BtBondState {
        self.get_bond_state_by_addr(&device.address)
    }

    fn set_pin(&self, device: BluetoothDevice, accept: bool, pin_code: Vec<u8>) -> bool {
        let addr = if let Some(addr) = RawAddress::from_string(device.address.clone()) {
            addr
        } else {
            warn!("Can't set pin. Address {} is not valid.", device.address);
            return false;
        };

        let is_bonding = match self.found_devices.get(&device.address) {
            Some(d) => d.bond_state == BtBondState::Bonding,
            None => false,
        };

        if !is_bonding {
            warn!("Can't set pin. Device {} isn't bonding.", DisplayAddress(&addr));
            return false;
        }

        let mut btpin = BtPinCode { pin: array_utils::to_sized_array(&pin_code) };

        self.intf.lock().unwrap().pin_reply(&addr, accept as u8, pin_code.len() as u8, &mut btpin)
            == 0
    }

    fn set_passkey(&self, device: BluetoothDevice, accept: bool, passkey: Vec<u8>) -> bool {
        let addr = if let Some(addr) = RawAddress::from_string(device.address.clone()) {
            addr
        } else {
            warn!("Can't set passkey. Address {} is not valid.", device.address);
            return false;
        };

        let is_bonding = match self.found_devices.get(&device.address) {
            Some(d) => d.bond_state == BtBondState::Bonding,
            None => false,
        };

        if !is_bonding {
            warn!("Can't set passkey. Device {} isn't bonding.", DisplayAddress(&addr));
            return false;
        }

        let mut tmp: [u8; 4] = [0; 4];
        tmp.copy_from_slice(passkey.as_slice());
        let passkey = u32::from_ne_bytes(tmp);

        self.intf.lock().unwrap().ssp_reply(
            &addr,
            BtSspVariant::PasskeyEntry,
            accept as u8,
            passkey,
        ) == 0
    }

    fn set_pairing_confirmation(&self, device: BluetoothDevice, accept: bool) -> bool {
        let addr = RawAddress::from_string(device.address.clone());

        if addr.is_none() {
            warn!("Can't set pairing confirmation. Address {} is not valid.", device.address);
            return false;
        }

        self.intf.lock().unwrap().ssp_reply(
            &addr.unwrap(),
            BtSspVariant::PasskeyConfirmation,
            accept as u8,
            0,
        ) == 0
    }

    fn get_remote_name(&self, device: BluetoothDevice) -> String {
        match self.get_remote_device_property(&device, &BtPropertyType::BdName) {
            Some(BluetoothProperty::BdName(name)) => return name.clone(),
            _ => return "".to_string(),
        }
    }

    fn get_remote_type(&self, device: BluetoothDevice) -> BtDeviceType {
        match self.get_remote_device_property(&device, &BtPropertyType::TypeOfDevice) {
            Some(BluetoothProperty::TypeOfDevice(device_type)) => return device_type,
            _ => return BtDeviceType::Unknown,
        }
    }

    fn get_remote_alias(&self, device: BluetoothDevice) -> String {
        match self.get_remote_device_property(&device, &BtPropertyType::RemoteFriendlyName) {
            Some(BluetoothProperty::RemoteFriendlyName(name)) => return name.clone(),
            _ => "".to_string(),
        }
    }

    fn set_remote_alias(&mut self, device: BluetoothDevice, new_alias: String) {
        let _ = self.set_remote_device_property(
            &device,
            BtPropertyType::RemoteFriendlyName,
            BluetoothProperty::RemoteFriendlyName(new_alias),
        );
    }

    fn get_remote_class(&self, device: BluetoothDevice) -> u32 {
        match self.get_remote_device_property(&device, &BtPropertyType::ClassOfDevice) {
            Some(BluetoothProperty::ClassOfDevice(class)) => return class,
            _ => 0,
        }
    }

    fn get_remote_appearance(&self, device: BluetoothDevice) -> u16 {
        match self.get_remote_device_property(&device, &BtPropertyType::Appearance) {
            Some(BluetoothProperty::Appearance(appearance)) => appearance,
            _ => 0,
        }
    }

    fn get_remote_connected(&self, device: BluetoothDevice) -> bool {
        self.get_connection_state(device) != BtConnectionState::NotConnected
    }

    fn get_remote_wake_allowed(&self, device: BluetoothDevice) -> bool {
        // Wake is allowed if the device supports HIDP or HOGP only.
        match self.get_remote_device_property(&device, &BtPropertyType::Uuids) {
            Some(BluetoothProperty::Uuids(uuids)) => {
                return uuids.iter().any(|&x| {
                    UuidHelper::is_known_profile(&x.uu).map_or(false, |profile| {
                        profile == Profile::Hid || profile == Profile::Hogp
                    })
                });
            }
            _ => false,
        }
    }

    fn get_remote_vendor_product_info(&self, device: BluetoothDevice) -> BtVendorProductInfo {
        match self.get_remote_device_property(&device, &BtPropertyType::VendorProductInfo) {
            Some(BluetoothProperty::VendorProductInfo(p)) => p.clone(),
            _ => BtVendorProductInfo { vendor_id_src: 0, vendor_id: 0, product_id: 0, version: 0 },
        }
    }

    fn get_remote_address_type(&self, device: BluetoothDevice) -> BtAddrType {
        match self.get_remote_device_property(&device, &BtPropertyType::RemoteAddrType) {
            Some(BluetoothProperty::RemoteAddrType(addr_type)) => addr_type,
            _ => BtAddrType::Unknown,
        }
    }

    fn get_remote_rssi(&self, device: BluetoothDevice) -> i8 {
        match self.get_remote_device_property(&device, &BtPropertyType::RemoteRssi) {
            Some(BluetoothProperty::RemoteRssi(rssi)) => rssi,
            _ => INVALID_RSSI,
        }
    }

    fn get_connected_devices(&self) -> Vec<BluetoothDevice> {
        let bonded_connected: HashMap<String, BluetoothDevice> = self
            .bonded_devices
            .iter()
            .filter(|(_, v)| v.acl_state == BtAclState::Connected)
            .map(|(k, v)| (k.clone(), v.info.clone()))
            .collect();
        let mut found_connected: Vec<BluetoothDevice> = self
            .found_devices
            .iter()
            .filter(|(k, v)| {
                v.acl_state == BtAclState::Connected
                    && !bonded_connected.contains_key(&k.to_string())
            })
            .map(|(_, v)| v.info.clone())
            .collect();

        let mut all =
            bonded_connected.iter().map(|(_, v)| v.clone()).collect::<Vec<BluetoothDevice>>();
        all.append(&mut found_connected);

        all
    }

    fn get_connection_state(&self, device: BluetoothDevice) -> BtConnectionState {
        let addr = RawAddress::from_string(device.address.clone());

        if addr.is_none() {
            warn!("Can't check connection state. Address {} is not valid.", device.address);
            return BtConnectionState::NotConnected;
        }

        // The underlying api adds whether this is ENCRYPTED_BREDR or ENCRYPTED_LE.
        // As long as it is non-zero, it is connected.
        self.intf.lock().unwrap().get_connection_state(&addr.unwrap())
    }

    fn get_profile_connection_state(&self, profile: Uuid128Bit) -> ProfileConnectionState {
        if let Some(known) = UuidHelper::is_known_profile(&profile) {
            match known {
                Profile::A2dpSink | Profile::A2dpSource => {
                    self.bluetooth_media.lock().unwrap().get_a2dp_connection_state()
                }
                Profile::Hfp | Profile::HfpAg => {
                    self.bluetooth_media.lock().unwrap().get_hfp_connection_state()
                }
                // TODO: (b/223431229) Profile::Hid and Profile::Hogp
                _ => ProfileConnectionState::Disconnected,
            }
        } else {
            ProfileConnectionState::Disconnected
        }
    }

    fn get_remote_uuids(&self, device: BluetoothDevice) -> Vec<Uuid128Bit> {
        match self.get_remote_device_property(&device, &BtPropertyType::Uuids) {
            Some(BluetoothProperty::Uuids(uuids)) => {
                return uuids.iter().map(|&x| x.uu.clone()).collect::<Vec<Uuid128Bit>>()
            }
            _ => return vec![],
        }
    }

    fn fetch_remote_uuids(&self, remote_device: BluetoothDevice) -> bool {
        let device = match self.get_remote_device_if_found(&remote_device.address) {
            Some(v) => v,
            None => {
                warn!("Won't fetch UUIDs on unknown device");
                return false;
            }
        };

        let mut addr = match RawAddress::from_string(device.info.address.clone()) {
            Some(v) => v,
            None => {
                warn!("Can't fetch UUIDs. Address {} is not valid.", device.info.address);
                return false;
            }
        };

        let transport = match self.get_remote_type(device.info.clone()) {
            BtDeviceType::Bredr => BtTransport::Bredr,
            BtDeviceType::Ble => BtTransport::Le,
            _ => device.acl_reported_transport,
        };

        self.intf.lock().unwrap().get_remote_services(&mut addr, transport) == 0
    }

    fn sdp_search(&self, device: BluetoothDevice, uuid: Uuid128Bit) -> bool {
        if self.sdp.is_none() {
            warn!("SDP is not initialized. Can't do SDP search.");
            return false;
        }

        let addr = RawAddress::from_string(device.address.clone());
        if addr.is_none() {
            warn!("Can't SDP search. Address {} is not valid.", device.address);
            return false;
        }

        let uu = Uuid::from(uuid);
        self.sdp.as_ref().unwrap().sdp_search(&mut addr.unwrap(), &uu) == BtStatus::Success
    }

    fn create_sdp_record(&mut self, sdp_record: BtSdpRecord) -> bool {
        let mut handle: i32 = -1;
        let mut sdp_record = sdp_record;
        match self.sdp.as_ref().unwrap().create_sdp_record(&mut sdp_record, &mut handle) {
            BtStatus::Success => {
                let record_clone = sdp_record.clone();
                self.callbacks.for_all_callbacks(|callback| {
                    callback.on_sdp_record_created(record_clone.clone(), handle);
                });
                true
            }
            _ => false,
        }
    }

    fn remove_sdp_record(&self, handle: i32) -> bool {
        self.sdp.as_ref().unwrap().remove_sdp_record(handle) == BtStatus::Success
    }

    fn connect_all_enabled_profiles(&mut self, device: BluetoothDevice) -> bool {
        // Profile init must be complete before this api is callable
        if !self.profiles_ready {
            return false;
        }

        let mut addr = match RawAddress::from_string(device.address.clone()) {
            Some(v) => v,
            None => {
                warn!("Can't connect profiles on invalid address [{}]", &device.address);
                return false;
            }
        };

        let is_connected = self
            .get_remote_device_if_found(&device.address)
            .map_or(false, |d| d.acl_state == BtAclState::Connected);
        if !is_connected {
            // log ACL connection attempt if it's not already connected.
            metrics::acl_connect_attempt(addr, BtAclState::Connected);
            // Pause discovery before connecting, or the ACL connection request may conflict with
            // the ongoing inquiry.
            self.pause_discovery();
        }

        // Check all remote uuids to see if they match enabled profiles and connect them.
        let mut has_enabled_uuids = false;
        let mut has_media_profile = false;
        let mut has_supported_profile = false;
        let uuids = self.get_remote_uuids(device.clone());
        for uuid in uuids.iter() {
            match UuidHelper::is_known_profile(uuid) {
                Some(p) => {
                    if UuidHelper::is_profile_supported(&p) {
                        match p {
                            Profile::Hid | Profile::Hogp => {
                                has_supported_profile = true;
                                let status = self.hh.as_ref().unwrap().connect(&mut addr);
                                metrics::profile_connection_state_changed(
                                    addr,
                                    p as u32,
                                    BtStatus::Success,
                                    BthhConnectionState::Connecting as u32,
                                );

                                if status != BtStatus::Success {
                                    metrics::profile_connection_state_changed(
                                        addr,
                                        p as u32,
                                        status,
                                        BthhConnectionState::Disconnected as u32,
                                    );
                                }
                            }

                            Profile::A2dpSink | Profile::A2dpSource | Profile::Hfp
                                if !has_media_profile =>
                            {
                                has_supported_profile = true;
                                has_media_profile = true;
                                let txl = self.tx.clone();
                                let address = device.address.clone();
                                topstack::get_runtime().spawn(async move {
                                    let _ = txl
                                        .send(Message::Media(MediaActions::Connect(address)))
                                        .await;
                                });
                            }

                            Profile::Bas => {
                                has_supported_profile = true;
                                let tx = self.tx.clone();
                                let transport =
                                    match self.get_remote_device_if_found(&device.address) {
                                        Some(context) => context.acl_reported_transport,
                                        None => return false,
                                    };
                                let device_to_send = device.clone();
                                let transport = match self.get_remote_type(device.clone()) {
                                    BtDeviceType::Bredr => BtTransport::Bredr,
                                    BtDeviceType::Ble => BtTransport::Le,
                                    _ => transport,
                                };
                                topstack::get_runtime().spawn(async move {
                                    let _ = tx
                                        .send(Message::BatteryService(
                                            BatteryServiceActions::Connect(
                                                device_to_send,
                                                transport,
                                            ),
                                        ))
                                        .await;
                                });
                            }

                            // We don't connect most profiles
                            _ => (),
                        }
                    }
                    has_enabled_uuids = true;
                }
                _ => {}
            }
        }

        // If SDP isn't completed yet, we wait for it to complete and retry the connection again.
        // Otherwise, this connection request is done, no retry is required.
        if !has_enabled_uuids {
            warn!("[{}] SDP hasn't completed for device, wait to connect.", DisplayAddress(&addr));
            if let Some(d) = self.get_remote_device_if_found_mut(&device.address) {
                if uuids.len() == 0 || !d.services_resolved {
                    d.wait_to_connect = true;
                }
            }
        }

        // If the SDP has not been completed or the device does not have a profile that we are
        // interested in connecting to, resume discovery now. Other cases will be handled in the
        // ACL connection state or bond state callbacks.
        if !has_enabled_uuids || !has_supported_profile {
            self.resume_discovery();
        }

        return true;
    }

    fn disconnect_all_enabled_profiles(&mut self, device: BluetoothDevice) -> bool {
        if !self.profiles_ready {
            return false;
        }

        let addr = RawAddress::from_string(device.address.clone());
        if addr.is_none() {
            warn!("Can't connect profiles on invalid address [{}]", &device.address);
            return false;
        }

        // log ACL disconnection attempt if it's not already disconnected.
        let is_connected = self
            .get_remote_device_if_found(&device.address)
            .map_or(false, |d| d.acl_state == BtAclState::Connected);
        if is_connected {
            metrics::acl_connect_attempt(addr.unwrap(), BtAclState::Disconnected);
        }

        let uuids = self.get_remote_uuids(device.clone());
        let mut has_media_profile = false;
        for uuid in uuids.iter() {
            match UuidHelper::is_known_profile(uuid) {
                Some(p) => {
                    if UuidHelper::is_profile_supported(&p) {
                        match p {
                            Profile::Hid | Profile::Hogp => {
                                self.hh.as_ref().unwrap().disconnect(&mut addr.unwrap(), true);
                            }

                            Profile::A2dpSink
                            | Profile::A2dpSource
                            | Profile::Hfp
                            | Profile::AvrcpController
                                if !has_media_profile =>
                            {
                                has_media_profile = true;
                                let txl = self.tx.clone();
                                let address = device.address.clone();
                                topstack::get_runtime().spawn(async move {
                                    let _ = txl
                                        .send(Message::Media(MediaActions::Disconnect(address)))
                                        .await;
                                });
                            }

                            Profile::Bas => {
                                let tx = self.tx.clone();
                                let device_to_send = device.clone();
                                topstack::get_runtime().spawn(async move {
                                    let _ = tx
                                        .send(Message::BatteryService(
                                            BatteryServiceActions::Disconnect(device_to_send),
                                        ))
                                        .await;
                                });
                            }

                            // We don't connect most profiles
                            _ => (),
                        }
                    }
                }
                _ => {}
            }
        }

        // Disconnect all socket connections
        if let Some(raw_addr) = RawAddress::from_string(device.address.clone()) {
            let txl = self.tx.clone();
            topstack::get_runtime().spawn(async move {
                let _ = txl
                    .send(Message::SocketManagerActions(SocketActions::DisconnectAll(raw_addr)))
                    .await;
            });
        }

        // Disconnect all GATT connections
        let txl = self.tx.clone();
        topstack::get_runtime().spawn(async move {
            let _ = txl.send(Message::GattActions(GattActions::Disconnect(device.clone()))).await;
        });

        return true;
    }

    fn is_wbs_supported(&self) -> bool {
        self.intf.lock().unwrap().get_wbs_supported()
    }

    fn is_swb_supported(&self) -> bool {
        self.intf.lock().unwrap().get_swb_supported()
    }

    fn get_supported_roles(&self) -> Vec<BtAdapterRole> {
        let mut roles: Vec<BtAdapterRole> = vec![];

        // See Core 5.3, Vol 4, Part E, 7.8.27 for detailed state information
        if self.le_supported_states >> 35 & 1 == 1u64 {
            roles.push(BtAdapterRole::Central);
        }
        if self.le_supported_states >> 38 & 1 == 1u64 {
            roles.push(BtAdapterRole::Peripheral);
        }
        if self.le_supported_states >> 28 & 1 == 1u64 {
            roles.push(BtAdapterRole::CentralPeripheral);
        }

        roles
    }

    fn is_coding_format_supported(&self, coding_format: EscoCodingFormat) -> bool {
        self.intf.lock().unwrap().is_coding_format_supported(coding_format as u8)
    }
}

impl BtifSdpCallbacks for Bluetooth {
    fn sdp_search(
        &mut self,
        status: BtStatus,
        address: RawAddress,
        uuid: Uuid,
        _count: i32,
        records: Vec<BtSdpRecord>,
    ) {
        let uuid_to_send = match UuidHelper::from_string(uuid.to_string()) {
            Some(uu) => uu,
            None => return,
        };
        let device_info = match self.get_remote_device_info_if_found(&address.to_string()) {
            Some(info) => info,
            None => BluetoothDevice::new(address.to_string(), "".to_string()),
        };

        // The SDP records we get back do not populate the UUID so we populate it ourselves before
        // sending them on.
        let mut records = records;
        records.iter_mut().for_each(|record| {
            match record {
                BtSdpRecord::HeaderOverlay(header) => header.uuid = uuid.clone(),
                BtSdpRecord::MapMas(record) => record.hdr.uuid = uuid.clone(),
                BtSdpRecord::MapMns(record) => record.hdr.uuid = uuid.clone(),
                BtSdpRecord::PbapPse(record) => record.hdr.uuid = uuid.clone(),
                BtSdpRecord::PbapPce(record) => record.hdr.uuid = uuid.clone(),
                BtSdpRecord::OppServer(record) => record.hdr.uuid = uuid.clone(),
                BtSdpRecord::SapServer(record) => record.hdr.uuid = uuid.clone(),
                BtSdpRecord::Dip(record) => record.hdr.uuid = uuid.clone(),
                BtSdpRecord::Mps(record) => record.hdr.uuid = uuid.clone(),
            };
        });
        self.callbacks.for_all_callbacks(|callback| {
            callback.on_sdp_search_complete(device_info.clone(), uuid_to_send, records.clone());
        });
        debug!(
            "Sdp search result found: Status({:?}) Address({}) Uuid({:?})",
            status,
            DisplayAddress(&address),
            uuid
        );
    }
}

impl BtifHHCallbacks for Bluetooth {
    fn connection_state(&mut self, mut address: RawAddress, state: BthhConnectionState) {
        debug!(
            "Hid host connection state updated: Address({}) State({:?})",
            DisplayAddress(&address),
            state
        );

        // HID or HOG is not differentiated by the hid host when callback this function. Assume HOG
        // if the device is LE only and HID if classic only. And assume HOG if UUID said so when
        // device type is dual or unknown.
        let device = BluetoothDevice::new(address.to_string(), "".to_string());
        let profile = match self.get_remote_type(device.clone()) {
            BtDeviceType::Ble => Profile::Hogp,
            BtDeviceType::Bredr => Profile::Hid,
            _ => {
                if self.get_remote_uuids(device).contains(&UuidHelper::from_string(HOGP).unwrap()) {
                    Profile::Hogp
                } else {
                    Profile::Hid
                }
            }
        };

        metrics::profile_connection_state_changed(
            address,
            profile as u32,
            BtStatus::Success,
            state as u32,
        );

        if BtBondState::Bonded != self.get_bond_state_by_addr(&address.to_string()) {
            warn!(
                "[{}]: Rejecting a unbonded device's attempt to connect to HID/HOG profiles",
                DisplayAddress(&address)
            );
            self.hh.as_ref().unwrap().disconnect(&mut address, true);
        }
    }

    fn hid_info(&mut self, address: RawAddress, info: BthhHidInfo) {
        debug!("Hid host info updated: Address({}) Info({:?})", DisplayAddress(&address), info);
    }

    fn protocol_mode(&mut self, address: RawAddress, status: BthhStatus, mode: BthhProtocolMode) {
        debug!(
            "Hid host protocol mode updated: Address({}) Status({:?}) Mode({:?})",
            DisplayAddress(&address),
            status,
            mode
        );
    }

    fn idle_time(&mut self, address: RawAddress, status: BthhStatus, idle_rate: i32) {
        debug!(
            "Hid host idle time updated: Address({}) Status({:?}) Idle Rate({:?})",
            DisplayAddress(&address),
            status,
            idle_rate
        );
    }

    fn get_report(&mut self, address: RawAddress, status: BthhStatus, _data: Vec<u8>, size: i32) {
        debug!(
            "Hid host got report: Address({}) Status({:?}) Report Size({:?})",
            DisplayAddress(&address),
            status,
            size
        );
    }

    fn handshake(&mut self, address: RawAddress, status: BthhStatus) {
        debug!("Hid host handshake: Address({}) Status({:?})", DisplayAddress(&address), status);
    }
}

// TODO(b/261143122): Remove these once we migrate to BluetoothQA entirely
impl IBluetoothQALegacy for Bluetooth {
    fn get_connectable(&self) -> bool {
        self.get_connectable_internal()
    }

    fn set_connectable(&mut self, mode: bool) -> bool {
        self.set_connectable_internal(mode)
    }

    fn get_alias(&self) -> String {
        self.get_alias_internal()
    }

    fn get_modalias(&self) -> String {
        format!("bluetooth:v00E0pC405d{:04x}", FLOSS_VER)
    }

    fn get_hid_report(
        &mut self,
        addr: String,
        report_type: BthhReportType,
        report_id: u8,
    ) -> BtStatus {
        self.get_hid_report_internal(addr, report_type, report_id)
    }

    fn set_hid_report(
        &mut self,
        addr: String,
        report_type: BthhReportType,
        report: String,
    ) -> BtStatus {
        self.set_hid_report_internal(addr, report_type, report)
    }

    fn send_hid_data(&mut self, addr: String, data: String) -> BtStatus {
        self.send_hid_data_internal(addr, data)
    }
}
